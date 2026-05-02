package com.miso.blog.publish.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import com.miso.blog.publish.dto.CreatePublishTargetRequest;
import com.miso.blog.publish.dto.PublishStrategyResponse;
import com.miso.blog.publish.dto.PublishTargetResponse;
import com.miso.blog.publish.dto.UpdatePublishTargetRequest;
import com.miso.blog.publish.entity.PublishTargetEntity;
import com.miso.blog.publish.repository.PublishTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublishTargetService {
    private final PublishTargetRepository publishTargetRepository;

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
