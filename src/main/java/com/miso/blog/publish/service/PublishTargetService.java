package com.miso.blog.publish.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import com.miso.blog.publish.dto.CreatePublishTargetRequest;
import com.miso.blog.publish.dto.GitHubBranchOptionResponse;
import com.miso.blog.publish.dto.GitHubContentCommitResult;
import com.miso.blog.publish.dto.GitHubContentFile;
import com.miso.blog.publish.dto.GitHubPagesConnectionTestResponse;
import com.miso.blog.publish.dto.GitHubRepositoryOptionResponse;
import com.miso.blog.publish.dto.JekyllScaffoldFileResponse;
import com.miso.blog.publish.dto.PublishStrategyResponse;
import com.miso.blog.publish.dto.PublishTargetResponse;
import com.miso.blog.publish.dto.SeedJekyllSiteRequest;
import com.miso.blog.publish.dto.SeedJekyllSiteResponse;
import com.miso.blog.publish.dto.UpdatePublishTargetRequest;
import com.miso.blog.publish.entity.PublishTargetEntity;
import com.miso.blog.publish.repository.PublishTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublishTargetService {
    private final PublishTargetRepository publishTargetRepository;
    private final GitHubContentsClient gitHubContentsClient;
    private final GitHubPagesTargetDefaults gitHubPagesTargetDefaults;
    private final JekyllSiteScaffoldFormatter jekyllSiteScaffoldFormatter;

    @Transactional
    public PublishTargetResponse createTarget(CreatePublishTargetRequest request) {
        PublishTargetEntity target = publishTargetRepository.save(PublishTargetEntity.builder()
                .channel(request.channel())
                .role(request.role())
                .name(request.name().trim())
                .baseUrl(trimToNull(request.baseUrl()))
                .repositoryFullName(trimToNull(request.repositoryFullName()))
                .branchName(defaultText(request.branchName(), "main"))
                .contentRootPath(defaultText(request.contentRootPath(), "_posts"))
                .customDomain(trimToNull(request.customDomain()))
                .active(request.active() == null || request.active())
                .build());
        return PublishTargetResponse.from(target);
    }

    @Transactional(readOnly = true)
    public List<PublishTargetResponse> getTargets() {
        return publishTargetRepository.findAllByOrderByRoleAscChannelAscIdAsc()
                .stream()
                .map(PublishTargetResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublishStrategyResponse getStrategy() {
        return new PublishStrategyResponse(
                PublishChannel.GITHUB_PAGES.name(),
                PublishChannel.VELOG.name(),
                "서버 DB의 Markdown을 원본으로 저장하고 GitHub Pages에는 Markdown 파일 commit, Velog에는 노출용 export를 제공합니다.",
                getTargets()
        );
    }

    @Transactional(readOnly = true)
    public List<GitHubRepositoryOptionResponse> getGitHubRepositories() {
        return gitHubContentsClient.listRepositories(gitHubPagesTargetDefaults.owner());
    }

    @Transactional(readOnly = true)
    public List<GitHubBranchOptionResponse> getGitHubBranches(String repositoryFullName) {
        return gitHubContentsClient.listBranches(repositoryFullName);
    }

    @Transactional(readOnly = true)
    public GitHubPagesConnectionTestResponse testGitHubPagesConnection(Long targetId) {
        PublishTargetEntity target = getTargetOrThrow(targetId);
        if (target.getChannel() != PublishChannel.GITHUB_PAGES) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행 대상만 연결 테스트를 실행할 수 있습니다.");
        }
        validateGitHubPagesTarget(target);

        String repositoryFullName = gitHubPagesTargetDefaults.repositoryFullName(target);
        String branchName = gitHubPagesTargetDefaults.branchName(target);
        String contentRootPath = gitHubPagesTargetDefaults.contentRootPath(target);
        GitHubContentsClient.GitHubConnectionCheckResult result = gitHubContentsClient.checkConnection(
                repositoryFullName,
                branchName,
                contentRootPath
        );
        List<String> warnings = new ArrayList<>(result.warnings());
        boolean jekyllReady = result.branchExists();
        if (result.branchExists()) {
            for (String filePath : jekyllSiteScaffoldFormatter.requiredFilePaths()) {
                if (!gitHubContentsClient.contentExists(repositoryFullName, branchName, filePath)) {
                    jekyllReady = false;
                    warnings.add("Jekyll 기본 파일이 없습니다: " + filePath + ". 필요하면 Jekyll 초기화 API를 실행하세요.");
                }
            }
        }

        return new GitHubPagesConnectionTestResponse(
                target.getId(),
                repositoryFullName,
                branchName,
                contentRootPath,
                true,
                result.branchExists(),
                jekyllReady,
                result.checkedItems(),
                warnings,
                result.repositoryUrl(),
                result.branchUrl(),
                result.contentRootUrl(),
                warnings.isEmpty()
                        ? "GitHub Pages 발행 설정 연결이 정상입니다."
                        : "GitHub 저장소는 확인됐지만 일부 경고가 있습니다.",
                LocalDateTime.now()
        );
    }

    public SeedJekyllSiteResponse seedJekyllSite(Long targetId, SeedJekyllSiteRequest request) {
        PublishTargetEntity target = getTargetOrThrow(targetId);
        if (target.getChannel() != PublishChannel.GITHUB_PAGES) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행 대상만 Jekyll 초기화를 실행할 수 있습니다.");
        }
        validateGitHubPagesTarget(target);

        String repositoryFullName = gitHubPagesTargetDefaults.repositoryFullName(target);
        String branchName = gitHubPagesTargetDefaults.branchName(target);
        String publicBaseUrl = defaultText(request == null ? null : request.baseUrl(), gitHubPagesTargetDefaults.resolvedPublicBaseUrl(target));
        boolean forceOverwrite = request != null && Boolean.TRUE.equals(request.forceOverwrite());
        List<GitHubContentFile> scaffoldFiles = jekyllSiteScaffoldFormatter.buildFiles(
                request == null ? null : request.siteTitle(),
                request == null ? null : request.siteDescription(),
                request == null ? null : request.authorName(),
                publicBaseUrl
        );

        List<GitHubContentFile> filesToCommit = new ArrayList<>();
        List<JekyllScaffoldFileResponse> fileResponses = new ArrayList<>();
        for (GitHubContentFile file : scaffoldFiles) {
            boolean exists = gitHubContentsClient.contentExists(repositoryFullName, branchName, file.filePath());
            if (exists && !forceOverwrite) {
                fileResponses.add(new JekyllScaffoldFileResponse(file.filePath(), "SKIPPED", null));
                continue;
            }
            filesToCommit.add(file);
            fileResponses.add(new JekyllScaffoldFileResponse(file.filePath(), exists ? "UPDATED" : "CREATED", null));
        }

        String commitSha = null;
        String commitUrl = null;
        if (!filesToCommit.isEmpty()) {
            List<GitHubContentCommitResult> commitResults = gitHubContentsClient.putFiles(
                    repositoryFullName,
                    branchName,
                    filesToCommit,
                    defaultText(request == null ? null : request.commitMessage(), "Initialize Jekyll tech blog")
            );
            Map<String, GitHubContentCommitResult> commitResultByPath = commitResults.stream()
                    .collect(Collectors.toMap(GitHubContentCommitResult::filePath, Function.identity()));
            fileResponses = fileResponses.stream()
                    .map(file -> {
                        GitHubContentCommitResult result = commitResultByPath.get(file.filePath());
                        return result == null
                                ? file
                                : new JekyllScaffoldFileResponse(file.filePath(), file.action(), result.contentUrl());
                    })
                    .toList();
            commitSha = commitResults.get(0).commitSha();
            commitUrl = commitResults.get(0).commitUrl();
        }

        return new SeedJekyllSiteResponse(
                target.getId(),
                repositoryFullName,
                branchName,
                publicBaseUrl,
                forceOverwrite,
                fileResponses,
                commitSha,
                commitUrl,
                LocalDateTime.now()
        );
    }

    @Transactional
    public List<PublishTargetResponse> createDefaultTargets() {
        if (!publishTargetRepository.findAll().isEmpty()) {
            return getTargets();
        }

        PublishTargetEntity emptyGitHubPagesTarget = PublishTargetEntity.builder()
                .channel(PublishChannel.GITHUB_PAGES)
                .role(PublishRole.PRIMARY)
                .name("GitHub Pages")
                .active(true)
                .build();
        publishTargetRepository.save(PublishTargetEntity.builder()
                .channel(PublishChannel.GITHUB_PAGES)
                .role(PublishRole.PRIMARY)
                .name("GitHub Pages")
                .baseUrl(gitHubPagesTargetDefaults.baseUrl(emptyGitHubPagesTarget))
                .repositoryFullName(gitHubPagesTargetDefaults.repositoryFullName(emptyGitHubPagesTarget))
                .branchName(gitHubPagesTargetDefaults.branchName(emptyGitHubPagesTarget))
                .contentRootPath(gitHubPagesTargetDefaults.contentRootPath(emptyGitHubPagesTarget))
                .customDomain(gitHubPagesTargetDefaults.customDomain(emptyGitHubPagesTarget))
                .active(true)
                .build());
        publishTargetRepository.save(PublishTargetEntity.builder()
                .channel(PublishChannel.VELOG)
                .role(PublishRole.EXPOSURE)
                .name("Velog")
                .active(true)
                .build());

        return getTargets();
    }

    @Transactional
    public PublishTargetResponse updateTarget(Long targetId, UpdatePublishTargetRequest request) {
        PublishTargetEntity target = getTargetOrThrow(targetId);
        target.update(
                request.role(),
                request.name().trim(),
                trimToNull(request.baseUrl()),
                trimToNull(request.repositoryFullName()),
                defaultText(request.branchName(), "main"),
                defaultText(request.contentRootPath(), "_posts"),
                trimToNull(request.customDomain()),
                request.active() == null || request.active()
        );
        return PublishTargetResponse.from(target);
    }

    private PublishTargetEntity getTargetOrThrow(Long targetId) {
        return publishTargetRepository.findById(targetId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "발행 대상을 찾을 수 없습니다."));
    }

    private void validateGitHubPagesTarget(PublishTargetEntity target) {
        if (!target.isActive()) {
            throw new GeneralException(ErrorCode.CONFLICT, "비활성화된 발행 대상입니다.");
        }
        if (gitHubPagesTargetDefaults.repositoryFullName(target) == null) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages repositoryFullName 또는 github.owner를 먼저 설정하세요.");
        }
        if (gitHubPagesTargetDefaults.branchName(target) == null) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages branchName을 먼저 설정하세요.");
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
