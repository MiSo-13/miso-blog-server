package com.miso.blog.localrepo.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.localrepo.dto.LocalGitSnapshot;
import com.miso.blog.localrepo.entity.LocalRepositoryEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class LocalGitRepositoryScanner {
    private static final int SOURCE_SUMMARY_LIMIT = 70000;
    private static final int PATCH_LIMIT_PER_COMMIT = 9000;
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);

    public String normalizeRepositoryPath(String localPath) {
        try {
            Path path = Path.of(localPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(path)) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "로컬 저장소 경로가 디렉터리가 아닙니다.");
            }

            String gitRoot = runGit(path, "rev-parse", "--show-toplevel").trim();
            if (gitRoot.isBlank()) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "Git 저장소 경로가 아닙니다.");
            }
            return Path.of(gitRoot).toAbsolutePath().normalize().toString();
        } catch (GeneralException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "로컬 Git 저장소 경로를 확인할 수 없습니다.");
        }
    }

    public LocalGitSnapshot scan(LocalRepositoryEntity repository, int commitLimit, boolean includeUncommittedChanges) {
        Path path = Path.of(repository.getLocalPath()).toAbsolutePath().normalize();
        String branchName = runGit(path, "rev-parse", "--abbrev-ref", "HEAD").trim();
        List<String> commitShas = readRecentCommitShas(path, commitLimit);

        StringBuilder builder = new StringBuilder();
        builder.append("repositoryName: ").append(repository.getName()).append('\n');
        builder.append("localPath: ").append(repository.getLocalPath()).append('\n');
        builder.append("branch: ").append(branchName).append('\n');
        builder.append("commitLimit: ").append(commitLimit).append('\n');
        builder.append("includeUncommittedChanges: ").append(includeUncommittedChanges).append("\n\n");

        appendCommandOutput(builder, "recent commit overview", runGit(path, "log", "-n", String.valueOf(commitLimit), "--date=iso-strict", "--pretty=format:%h | %aI | %an | %s"));

        for (String sha : commitShas) {
            builder.append("\n## commit ").append(sha).append('\n');
            appendCommandOutput(builder, "changed files", runGit(path, "show", "--format=", "--name-status", sha));
            appendCommandOutput(builder, "patch", limit(runGit(path, "show", "--format=", "--find-renames", "--find-copies", "--unified=80", "--no-ext-diff", sha), PATCH_LIMIT_PER_COMMIT));
            if (builder.length() >= SOURCE_SUMMARY_LIMIT) {
                builder.append("\n... local source summary truncated ...");
                return new LocalGitSnapshot(branchName, builder.toString());
            }
        }

        if (includeUncommittedChanges) {
            builder.append("\n## uncommitted changes\n");
            appendCommandOutput(builder, "status", runGit(path, "status", "--short"));
            appendCommandOutput(builder, "diff stat", runGit(path, "diff", "--stat"));
            appendCommandOutput(builder, "diff patch", limit(runGit(path, "diff", "--unified=80", "--no-ext-diff"), PATCH_LIMIT_PER_COMMIT));
        }

        return new LocalGitSnapshot(branchName, limit(builder.toString(), SOURCE_SUMMARY_LIMIT));
    }

    private List<String> readRecentCommitShas(Path path, int commitLimit) {
        String output = runGit(path, "log", "-n", String.valueOf(commitLimit), "--pretty=format:%H");
        List<String> shas = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                shas.add(trimmed);
            }
        }
        return shas;
    }

    private void appendCommandOutput(StringBuilder builder, String title, String output) {
        builder.append("### ").append(title).append('\n');
        if (output == null || output.isBlank()) {
            builder.append("(없음)\n");
            return;
        }
        builder.append(output).append('\n');
    }

    private String runGit(Path directory, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
            boolean finished = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GeneralException(ErrorCode.BAD_REQUEST, "Git 명령 시간이 초과되었습니다.");
            }
            String output = outputFuture.get();
            if (process.exitValue() != 0) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "Git 명령 실행에 실패했습니다. " + output);
            }
            return output;
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "git 명령을 실행할 수 없습니다. Git 설치와 PATH를 확인해주세요.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "Git 명령 실행이 중단되었습니다.");
        } catch (ExecutionException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "Git 명령 출력을 읽을 수 없습니다.");
        }
    }

    private String readProcessOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String limit(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "\n... truncated ...";
    }
}
