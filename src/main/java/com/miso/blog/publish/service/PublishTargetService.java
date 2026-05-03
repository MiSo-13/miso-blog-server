package com.miso.blog.publish.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import com.miso.blog.publish.dto.CreatePublishTargetRequest;
import com.miso.blog.publish.dto.GitHubPagesConnectionTestResponse;
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
                "서버 DB의 Markdown을 원본으로 저장하고, GitHub Pages에는 Markdown 파일 commit, Velog에는 노출용 재발행을 목표로 합니다.",
                getTargets()
        );
    }

    @Transactional(readOnly = true)
    public GitHubPagesConnectionTestResponse testGitHubPagesConnection(Long targetId) {
        PublishTargetEntity target = getTargetOrThrow(targetId);
        if (target.getChannel() != PublishChannel.GITHUB_PAGES) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행 대상만 연결 테스트를 실행할 수 있습니다.");
        }
        validateGitHubPagesTarget(target);

        GitHubContentsClient.GitHubConnectionCheckResult result = gitHubContentsClient.checkConnection(
                target.getRepositoryFullName(),
                target.getBranchName(),
                target.getContentRootPath()
        );

        return new GitHubPagesConnectionTestResponse(
                target.getId(),
                target.getRepositoryFullName(),
                target.getBranchName(),
                target.getContentRootPath(),
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

        // 초기 전략: GitHub Pages를 원본 발행 채널로, Velog를 노출 채널로 둡니다.
        publishTargetRepository.save(PublishTargetEntity.builder()
                .channel(PublishChannel.GITHUB_PAGES)
                .role(PublishRole.PRIMARY)
                .name("GitHub Pages")
                .branchName("main")
                .contentRootPath("_posts")
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
        if (target.getRepositoryFullName() == null || target.getRepositoryFullName().isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages repositoryFullName을 먼저 설정하세요.");
        }
        if (target.getBranchName() == null || target.getBranchName().isBlank()) {
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
