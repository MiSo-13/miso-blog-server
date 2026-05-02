package com.miso.blog.git.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.git.code.GitAnalysisStatus;
import com.miso.blog.git.entity.GitAnalysisReportEntity;

import java.time.LocalDateTime;
import java.util.List;

public record GitAnalysisReportResponse(
        Long id,
        Long repositoryId,
        GitAnalysisStatus status,
        int commitLimit,
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
    public static GitAnalysisReportResponse from(GitAnalysisReportEntity report, ObjectMapper objectMapper) {
        return new GitAnalysisReportResponse(
                report.getId(),
                report.getRepository().getId(),
                report.getStatus(),
                report.getCommitLimit(),
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
