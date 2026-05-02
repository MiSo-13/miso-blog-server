package com.miso.blog.localrepo.dto;

import com.miso.blog.git.dto.TopicCandidateResponse;

import java.util.List;

public record LocalOnlyAnalysisResult(
        String analysisSummary,
        List<String> keywords,
        List<TopicCandidateResponse> topicCandidates,
        String recommendedTitle,
        String draftMarkdown
) {
}
