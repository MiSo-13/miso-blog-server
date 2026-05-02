package com.miso.blog.publish.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import com.miso.blog.post.service.BlogPostService;
import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import com.miso.blog.publish.dto.GitHubContentCommitResult;
import com.miso.blog.publish.dto.PublishGithubPagesRequest;
import com.miso.blog.publish.dto.PublishGithubPagesResponse;
import com.miso.blog.publish.entity.PublishTargetEntity;
import com.miso.blog.publish.repository.PublishTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogPostPublishService {
    private final BlogPostRepository blogPostRepository;
    private final PublishTargetRepository publishTargetRepository;
    private final BlogPostService blogPostService;
    private final GitHubPagesPostFormatter gitHubPagesPostFormatter;
    private final GitHubContentsClient gitHubContentsClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public PublishGithubPagesResponse publishToGitHubPages(Long blogPostId, PublishGithubPagesRequest request) {
        BlogPostEntity blogPost = getBlogPostOrThrow(blogPostId);
        if (blogPost.getStatus() != BlogPostStatus.APPROVED) {
            throw new GeneralException(ErrorCode.CONFLICT, "승인된 글만 GitHub Pages에 발행할 수 있습니다.");
        }

        PublishTargetEntity target = resolveGitHubPagesTarget(request == null ? null : request.targetId());
        validateTarget(target);

        LocalDateTime publishedAt = LocalDateTime.now();
        List<String> tags = readTags(blogPost.getTagsJson());
        String filePath = gitHubPagesPostFormatter.buildFilePath(target.getContentRootPath(), blogPost, publishedAt);
        String markdown = gitHubPagesPostFormatter.buildMarkdown(blogPost, tags, publishedAt);
        String commitMessage = resolveCommitMessage(request, blogPost);

        // GitHub commit이 성공한 뒤에만 내부 발행 상태를 전환한다.
        GitHubContentCommitResult commitResult = gitHubContentsClient.putFile(
                target.getRepositoryFullName(),
                target.getBranchName(),
                filePath,
                markdown,
                commitMessage
        );
        blogPostService.markPublished(blogPostId);

        return new PublishGithubPagesResponse(
                blogPost.getId(),
                BlogPostStatus.PUBLISHED.name(),
                target.getId(),
                target.getRepositoryFullName(),
                target.getBranchName(),
                commitResult.filePath(),
                commitResult.commitSha(),
                commitResult.commitUrl(),
                commitResult.contentUrl(),
                gitHubPagesPostFormatter.buildExpectedPublicUrl(resolveBaseUrl(target), blogPost, publishedAt)
        );
    }

    private BlogPostEntity getBlogPostOrThrow(Long blogPostId) {
        return blogPostRepository.findById(blogPostId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "블로그 글을 찾을 수 없습니다."));
    }

    private PublishTargetEntity resolveGitHubPagesTarget(Long targetId) {
        if (targetId != null) {
            PublishTargetEntity target = publishTargetRepository.findById(targetId)
                    .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "발행 대상을 찾을 수 없습니다."));
            if (target.getChannel() != PublishChannel.GITHUB_PAGES) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행 대상만 선택할 수 있습니다.");
            }
            return target;
        }

        return publishTargetRepository.findAll().stream()
                .filter(PublishTargetEntity::isActive)
                .filter(target -> target.getChannel() == PublishChannel.GITHUB_PAGES)
                .min(Comparator.comparing((PublishTargetEntity target) -> target.getRole() == PublishRole.PRIMARY ? 0 : 1)
                        .thenComparing(PublishTargetEntity::getId))
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "활성화된 GitHub Pages 발행 대상이 없습니다."));
    }

    private void validateTarget(PublishTargetEntity target) {
        if (!target.isActive()) {
            throw new GeneralException(ErrorCode.CONFLICT, "비활성화된 발행 대상입니다.");
        }
        if (target.getRepositoryFullName() == null || target.getRepositoryFullName().isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행 대상의 repositoryFullName을 설정해야 합니다.");
        }
        if (target.getBranchName() == null || target.getBranchName().isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행 대상의 branchName을 설정해야 합니다.");
        }
    }

    private String resolveCommitMessage(PublishGithubPagesRequest request, BlogPostEntity blogPost) {
        if (request != null && request.commitMessage() != null && !request.commitMessage().isBlank()) {
            return request.commitMessage().trim();
        }
        return "Publish blog post: " + blogPost.getTitle();
    }

    private String resolveBaseUrl(PublishTargetEntity target) {
        if (target.getCustomDomain() != null && !target.getCustomDomain().isBlank()) {
            return target.getCustomDomain().startsWith("http")
                    ? target.getCustomDomain()
                    : "https://" + target.getCustomDomain();
        }
        return target.getBaseUrl();
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
}
