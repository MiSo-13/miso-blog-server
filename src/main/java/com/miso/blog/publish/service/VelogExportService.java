package com.miso.blog.publish.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import com.miso.blog.publish.dto.ExportVelogMarkdownRequest;
import com.miso.blog.publish.dto.ExportVelogMarkdownResponse;
import com.miso.blog.publish.entity.PublishTargetEntity;
import com.miso.blog.publish.repository.PublishTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VelogExportService {
    private final BlogPostRepository blogPostRepository;
    private final PublishTargetRepository publishTargetRepository;
    private final VelogMarkdownFormatter velogMarkdownFormatter;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ExportVelogMarkdownResponse exportMarkdown(Long blogPostId, ExportVelogMarkdownRequest request) {
        BlogPostEntity blogPost = blogPostRepository.findById(blogPostId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "블로그 글을 찾을 수 없습니다."));
        if (blogPost.getStatus() != BlogPostStatus.APPROVED && blogPost.getStatus() != BlogPostStatus.PUBLISHED) {
            throw new GeneralException(ErrorCode.CONFLICT, "승인 또는 발행된 글만 Velog 노출용으로 export할 수 있습니다.");
        }

        PublishTargetEntity target = resolveVelogTarget(request == null ? null : request.targetId());
        String canonicalUrl = trimToNull(request == null ? null : request.canonicalUrl());
        boolean includeCanonicalLink = request == null || request.includeCanonicalLink() == null || request.includeCanonicalLink();
        boolean includeSourceNote = request != null && Boolean.TRUE.equals(request.includeSourceNote());
        List<String> tags = readTags(blogPost.getTagsJson());

        return new ExportVelogMarkdownResponse(
                blogPost.getId(),
                target == null ? null : target.getId(),
                target == null ? null : target.getName(),
                blogPost.getTitle(),
                blogPost.getSummary(),
                tags,
                velogMarkdownFormatter.buildMarkdown(blogPost, canonicalUrl, includeCanonicalLink, includeSourceNote),
                canonicalUrl,
                "Velog 글쓰기 화면에 title, markdown, tags를 복사해 노출용 글로 발행하면 됩니다."
        );
    }

    private PublishTargetEntity resolveVelogTarget(Long targetId) {
        if (targetId != null) {
            PublishTargetEntity target = publishTargetRepository.findById(targetId)
                    .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "발행 대상을 찾을 수 없습니다."));
            if (target.getChannel() != PublishChannel.VELOG) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "Velog 발행 대상만 선택할 수 있습니다.");
            }
            if (!target.isActive()) {
                throw new GeneralException(ErrorCode.CONFLICT, "비활성화된 Velog 발행 대상입니다.");
            }
            return target;
        }

        return publishTargetRepository.findAll().stream()
                .filter(PublishTargetEntity::isActive)
                .filter(target -> target.getChannel() == PublishChannel.VELOG)
                .min(Comparator.comparing((PublishTargetEntity target) -> target.getRole() == PublishRole.EXPOSURE ? 0 : 1)
                        .thenComparing(PublishTargetEntity::getId))
                .orElse(null);
    }

    private List<String> readTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
