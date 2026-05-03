package com.miso.blog.publish.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import com.miso.blog.publish.dto.CreatePublishTargetRequest;
import com.miso.blog.publish.dto.GitHubBranchOptionResponse;
import com.miso.blog.publish.dto.GitHubPagesConnectionTestResponse;
import com.miso.blog.publish.dto.GitHubRepositoryOptionResponse;
import com.miso.blog.publish.dto.PublishStrategyResponse;
import com.miso.blog.publish.dto.PublishTargetResponse;
import com.miso.blog.publish.dto.UpdatePublishTargetRequest;
import com.miso.blog.publish.entity.PublishTargetEntity;
import com.miso.blog.publish.repository.PublishTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublishTargetService {
    private final PublishTargetRepository publishTargetRepository;
    private final GitHubContentsClient gitHubContentsClient;
    private final GitHubPagesTargetDefaults gitHubPagesTargetDefaults;

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

        return new GitHubPagesConnectionTestResponse(
                target.getId(),
                repositoryFullName,
                branchName,
                contentRootPath,
                true,
                result.checkedItems(),
                result.warnings(),
                result.repositoryUrl(),
                result.branchUrl(),
                result.contentRootUrl(),
                result.warnings().isEmpty()
                        ? "GitHub Pages 발행 설정 연결이 정상입니다."
                        : "GitHub 저장소와 브랜치는 확인됐지만 일부 경고가 있습니다.",
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
