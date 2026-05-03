package com.miso.blog.localrepo.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.common.security.SecretMaskingService;
import com.miso.blog.localrepo.dto.CloneGitHubRepositoryRequest;
import com.miso.blog.localrepo.dto.LocalRepositoryResponse;
import com.miso.blog.localrepo.entity.LocalRepositoryEntity;
import com.miso.blog.localrepo.repository.LocalRepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitHubRepositoryCloneService {
    private static final Pattern REPOSITORY_FULL_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(3);

    private final LocalRepositoryRepository localRepositoryRepository;
    private final LocalGitRepositoryScanner localGitRepositoryScanner;
    private final SecretMaskingService secretMaskingService;

    @Value("${blog.github.token:}")
    private String githubToken;

    @Value("${blog.local-repositories.clone-base-dir:/app/repositories}")
    private String cloneBaseDir;

    @Transactional
    public LocalRepositoryResponse cloneAndRegister(CloneGitHubRepositoryRequest request) {
        validateToken();
        String repositoryFullName = normalizeRepositoryFullName(request.repositoryFullName());
        String branchName = trimToNull(request.branchName());
        Path targetPath = resolveTargetPath(repositoryFullName, branchName);

        cloneOrRefresh(repositoryFullName, branchName, targetPath, request.refreshExisting());

        String normalizedPath = localGitRepositoryScanner.normalizeRepositoryPath(targetPath.toString());
        Optional<LocalRepositoryEntity> existingRepository = localRepositoryRepository.findByLocalPath(normalizedPath);
        if (existingRepository.isPresent()) {
            return LocalRepositoryResponse.from(existingRepository.get());
        }

        LocalRepositoryEntity repository = localRepositoryRepository.save(LocalRepositoryEntity.builder()
                .name(defaultText(request.name(), repositoryFullName.substring(repositoryFullName.indexOf('/') + 1)))
                .localPath(normalizedPath)
                .defaultBranch(defaultText(branchName, "main"))
                .description(defaultText(request.description(), "GitHub에서 clone한 개발 블로그 분석 대상: " + repositoryFullName))
                .active(true)
                .build());
        return LocalRepositoryResponse.from(repository);
    }

    private void cloneOrRefresh(String repositoryFullName, String branchName, Path targetPath, Boolean refreshExisting) {
        try {
            Files.createDirectories(targetPath.getParent());
            if (Files.exists(targetPath.resolve(".git"))) {
                if (Boolean.FALSE.equals(refreshExisting)) {
                    return;
                }
                // 이미 clone된 저장소는 최신 이력을 받아 분석 기준을 갱신합니다.
                runGit(targetPath, CLONE_TIMEOUT, gitArgs("fetch", "origin", "--prune"));
                if (branchName != null) {
                    runGit(targetPath, CLONE_TIMEOUT, gitArgs("checkout", branchName));
                    runGit(targetPath, CLONE_TIMEOUT, gitArgs("pull", "--ff-only", "origin", branchName));
                } else {
                    runGit(targetPath, CLONE_TIMEOUT, gitArgs("pull", "--ff-only"));
                }
                return;
            }

            if (Files.exists(targetPath) && hasAnyFile(targetPath)) {
                throw new GeneralException(ErrorCode.CONFLICT, "clone 대상 경로가 비어있지 않습니다.");
            }

            List<String> cloneArgs = new ArrayList<>();
            cloneArgs.add("clone");
            if (branchName != null) {
                cloneArgs.add("--branch");
                cloneArgs.add(branchName);
                cloneArgs.add("--single-branch");
            }
            cloneArgs.add("https://github.com/" + repositoryFullName + ".git");
            cloneArgs.add(targetPath.toString());
            runGit(targetPath.getParent(), CLONE_TIMEOUT, gitArgs(cloneArgs.toArray(String[]::new)));
        } catch (GeneralException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 저장소 clone 경로를 준비할 수 없습니다.");
        }
    }

    private List<String> gitArgs(String... args) {
        return new ArrayList<>(List.of(args));
    }

    private void runGit(Path directory, Duration timeout, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("GIT_CONFIG_COUNT", "1");
        processBuilder.environment().put("GIT_CONFIG_KEY_0", "http.https://github.com/.extraheader");
        processBuilder.environment().put("GIT_CONFIG_VALUE_0", "Authorization: Bearer " + githubToken);

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 저장소 clone 시간이 초과되었습니다.");
            }
            String output = secretMaskingService.mask(outputFuture.get());
            if (process.exitValue() != 0) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 저장소 clone에 실패했습니다. " + output);
            }
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "git 명령을 실행할 수 없습니다. Docker 이미지에 Git이 설치되어 있는지 확인해주세요.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 저장소 clone이 중단되었습니다.");
        } catch (ExecutionException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 저장소 clone 결과를 읽을 수 없습니다.");
        }
    }

    private String readProcessOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean hasAnyFile(Path path) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(path)) {
            return stream.findAny().isPresent();
        }
    }

    private Path resolveTargetPath(String repositoryFullName, String branchName) {
        Path basePath = Path.of(cloneBaseDir).toAbsolutePath().normalize();
        String branchSuffix = branchName == null ? "default" : sanitize(branchName);
        Path targetPath = basePath.resolve(sanitize(repositoryFullName.replace("/", "__")) + "__" + branchSuffix).normalize();
        if (!targetPath.startsWith(basePath)) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "clone 대상 경로가 올바르지 않습니다.");
        }
        return targetPath;
    }

    private String normalizeRepositoryFullName(String repositoryFullName) {
        String normalized = trimToNull(repositoryFullName);
        if (normalized == null || !REPOSITORY_FULL_NAME_PATTERN.matcher(normalized).matches()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 저장소 full name이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private void validateToken() {
        if (githubToken == null || githubToken.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub token이 설정되어 있지 않습니다.");
        }
    }

    private String defaultText(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
