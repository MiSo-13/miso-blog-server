package com.miso.blog.ai.job.service;

import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.dto.ReviseBlogPostWithAiRequest;
import com.miso.blog.post.service.BlogPostRevisionService;
import com.miso.blog.post.service.GeneralBlogPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiJobWorker {
    private final AiJobStateService aiJobStateService;
    private final GeneralBlogPostService generalBlogPostService;
    private final BlogPostRevisionService blogPostRevisionService;

    @Async
    public void runGeneralBlogDraftJob(Long jobId, CreateGeneralBlogPostRequest request) {
        aiJobStateService.markRunning(jobId);
        try {
            BlogPostResponse response = generalBlogPostService.createAiDraft(request);
            aiJobStateService.markSucceeded(jobId, response, response.id());
        } catch (Exception exception) {
            aiJobStateService.markFailed(jobId, exception.getMessage());
        }
    }

    @Async
    public void runBlogPostRevisionJob(Long jobId, Long blogPostId, ReviseBlogPostWithAiRequest request) {
        aiJobStateService.markRunning(jobId);
        try {
            BlogPostResponse response = blogPostRevisionService.reviseWithAi(blogPostId, request);
            aiJobStateService.markSucceeded(jobId, response, response.id());
        } catch (Exception exception) {
            aiJobStateService.markFailed(jobId, exception.getMessage());
        }
    }
}
