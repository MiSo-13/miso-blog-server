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
    private static final int DEEP_SOURCE_SUMMARY_LIMIT = 100000;
    private static final int PATCH_LIMIT_PER_COMMIT = 9000;
    private static final int CODE_CONTEXT_FILE_LIMIT = 12;
    private static final int CODE_CONTEXT_PER_FILE_LIMIT = 7000;
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

    public LocalGitSnapshot scan(LocalRepositoryEntity repository, int commitLimit, boolean includeUncommittedChanges, boolean deepAnalysis) {
        Path path = Path.of(repository.getLocalPath()).toAbsolutePath().normalize();
        String branchName = runGit(path, "rev-parse", "--abbrev-ref", "HEAD").trim();
        List<String> commitShas = readRecentCommitShas(path, commitLimit);
        int sourceSummaryLimit = deepAnalysis ? DEEP_SOURCE_SUMMARY_LIMIT : SOURCE_SUMMARY_LIMIT;
        List<String> changedSourceFiles = new ArrayList<>();

        StringBuilder builder = new StringBuilder();
        builder.append("repositoryName: ").append(repository.getName()).append('\n');
        builder.append("localPath: ").append(repository.getLocalPath()).append('\n');
        builder.append("branch: ").append(branchName).append('\n');
        builder.append("commitLimit: ").append(commitLimit).append('\n');
        builder.append("includeUncommittedChanges: ").append(includeUncommittedChanges).append("\n\n");
        builder.append("deepAnalysis: ").append(deepAnalysis).append("\n\n");

        appendCommandOutput(builder, "recent commit overview", runGit(path, "log", "-n", String.valueOf(commitLimit), "--date=iso-strict", "--pretty=format:%h | %aI | %an | %s"));

        for (String sha : commitShas) {
            builder.append("\n## commit ").append(sha).append('\n');
            appendCommandOutput(builder, "changed files", runGit(path, "show", "--format=", "--name-status", sha));
            if (deepAnalysis) {
                collectChangedSourceFiles(changedSourceFiles, runGit(path, "show", "--format=", "--name-only", sha));
            }
            appendCommandOutput(builder, "patch", limit(runGit(path, "show", "--format=", "--find-renames", "--find-copies", "--unified=80", "--no-ext-diff", sha), PATCH_LIMIT_PER_COMMIT));
            if (builder.length() >= sourceSummaryLimit) {
                builder.append("\n... local source summary truncated ...");
                return new LocalGitSnapshot(branchName, builder.toString());
            }
        }

        if (includeUncommittedChanges) {
            builder.append("\n## uncommitted changes\n");
            appendCommandOutput(builder, "status", runGit(path, "status", "--short"));
            appendCommandOutput(builder, "diff stat", runGit(path, "diff", "--stat"));
            if (deepAnalysis) {
                collectChangedSourceFiles(changedSourceFiles, runGit(path, "diff", "--name-only"));
            }
            appendCommandOutput(builder, "diff patch", limit(runGit(path, "diff", "--unified=80", "--no-ext-diff"), PATCH_LIMIT_PER_COMMIT));
        }

        if (deepAnalysis && !changedSourceFiles.isEmpty()) {
            appendCurrentSourceContext(builder, path, changedSourceFiles, sourceSummaryLimit);
        }

        return new LocalGitSnapshot(branchName, limit(builder.toString(), sourceSummaryLimit));
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

    private void collectChangedSourceFiles(List<String> changedSourceFiles, String output) {
        if (output == null || output.isBlank()) {
            return;
        }
        for (String line : output.split("\\R")) {
            String filePath = line.trim();
            if (isReadableSourceFile(filePath) && !changedSourceFiles.contains(filePath)) {
                changedSourceFiles.add(filePath);
            }
        }
    }

    private void appendCurrentSourceContext(StringBuilder builder, Path repositoryRoot, List<String> changedSourceFiles, int sourceSummaryLimit) {
        builder.append("\n## current source context for deeper analysis\n");
        builder.append("The following snippets are current working tree file contents for files touched by recent commits. Use them to infer structure and implementation intent without copying long code blocks into the blog.\n\n");

        int appendedCount = 0;
        for (String filePath : changedSourceFiles) {
            if (appendedCount >= CODE_CONTEXT_FILE_LIMIT || builder.length() >= sourceSummaryLimit) {
                break;
            }
            Path resolvedPath = repositoryRoot.resolve(filePath).normalize();
            if (!resolvedPath.startsWith(repositoryRoot) || !Files.isRegularFile(resolvedPath)) {
                continue;
            }
            try {
                String content = Files.readString(resolvedPath, StandardCharsets.UTF_8);
                builder.append("### file ").append(filePath).append('\n');
                builder.append("```").append(languageHint(filePath)).append('\n')
                        .append(limit(content, CODE_CONTEXT_PER_FILE_LIMIT))
                        .append("\n```\n\n");
                appendedCount++;
            } catch (IOException exception) {
                builder.append("### file ").append(filePath).append("\n(read failed)\n\n");
            }
        }
    }

    private boolean isReadableSourceFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String lower = filePath.toLowerCase();
        if (lower.contains("application-private") || lower.endsWith(".lock") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".ico")
                || lower.endsWith(".pdf") || lower.endsWith(".zip") || lower.endsWith(".jar")) {
            return false;
        }
        return lower.endsWith(".java")
                || lower.endsWith(".kt")
                || lower.endsWith(".js")
                || lower.endsWith(".ts")
                || lower.endsWith(".tsx")
                || lower.endsWith(".jsx")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".properties")
                || lower.endsWith(".gradle")
                || lower.endsWith(".md")
                || lower.endsWith(".json")
                || lower.endsWith(".sql")
                || lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".scss");
    }

    private String languageHint(String filePath) {
        String lower = filePath == null ? "" : filePath.toLowerCase();
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".kt")) {
            return "kotlin";
        }
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) {
            return "javascript";
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) {
            return "typescript";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "yaml";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".sql")) {
            return "sql";
        }
        if (lower.endsWith(".html")) {
            return "html";
        }
        if (lower.endsWith(".css") || lower.endsWith(".scss")) {
            return "css";
        }
        return "";
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
