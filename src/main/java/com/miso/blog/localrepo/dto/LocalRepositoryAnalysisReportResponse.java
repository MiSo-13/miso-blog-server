package com.miso.blog.localrepo.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.git.code.GitAnalysisStatus;
import com.miso.blog.git.dto.TopicCandidateResponse;
import com.miso.blog.localrepo.code.LocalRepositoryAnalysisMode;
import com.miso.blog.localrepo.entity.LocalRepositoryAnalysisReportEntity;

import java.time.LocalDateTime;
import java.util.List;

public record LocalRepositoryAnalysisReportResponse(
        Long id,
        Long localRepositoryId,
        GitAnalysisStatus status,
        LocalRepositoryAnalysisMode analysisMode,
        int commitLimit,
        boolean includeUncommittedChanges,
        String focus,
        String sourceSummary,
        String analysisSummary,
        List<String> keywords,
        List<TopicCandidateResponse> topicCandidates,
        String recommendedTitle,
        String draftMarkdown,
        Long createdBlogPostId,
        String modelName,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LocalRepositoryAnalysisReportResponse from(
            LocalRepositoryAnalysisReportEntity report,
            ObjectMapper objectMapper
    ) {
        return new LocalRepositoryAnalysisReportResponse(
                report.getId(),
                report.getLocalRepository().getId(),
                report.getStatus(),
                report.getAnalysisMode(),
                report.getCommitLimit(),
                report.isIncludeUncommittedChanges(),
                report.getFocus(),
                report.getSourceSummary(),
                report.getAnalysisSummary(),
                readList(report.getKeywordsJson(), objectMapper, new TypeReference<>() {
                }),
                readList(report.getTopicCandidatesJson(), objectMapper, new TypeReference<>() {
                }),
                report.getRecommendedTitle(),
                report.getDraftMarkdown(),
                report.getCreatedBlogPostId(),
                report.getModelName(),
                report.getErrorMessage(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private static <T> List<T> readList(String json, ObjectMapper objectMapper, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
