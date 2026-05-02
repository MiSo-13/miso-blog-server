package com.miso.blog.git.dto;

import java.util.List;

public record OpenAiGitAnalysisResult(
        String analysisSummary,
        List<String> keywords,
        List<TopicCandidateResponse> topicCandidates,
        String recommendedTitle,
        String draftMarkdown,
        String rawResponse,
        String modelName,
        long inputTokens,
        long cachedInputTokens,
        long outputTokens
) {
}
