package com.miso.blog.localrepo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.git.code.GitAnalysisStatus;
import com.miso.blog.git.dto.OpenAiGitAnalysisResult;
import com.miso.blog.git.service.OpenAiGitAnalysisClient;
import com.miso.blog.localrepo.code.LocalRepositoryAnalysisMode;
import com.miso.blog.localrepo.dto.AnalyzeLocalRepositoryRequest;
import com.miso.blog.localrepo.dto.CreateLocalRepositoryRequest;
import com.miso.blog.localrepo.dto.LocalGitSnapshot;
import com.miso.blog.localrepo.dto.LocalOnlyAnalysisResult;
import com.miso.blog.localrepo.dto.LocalRepositoryAnalysisReportResponse;
import com.miso.blog.localrepo.dto.LocalRepositoryResponse;
import com.miso.blog.localrepo.dto.UpdateLocalRepositoryRequest;
import com.miso.blog.localrepo.entity.LocalRepositoryAnalysisReportEntity;
import com.miso.blog.localrepo.entity.LocalRepositoryEntity;
import com.miso.blog.localrepo.repository.LocalRepositoryAnalysisReportRepository;
import com.miso.blog.localrepo.repository.LocalRepositoryRepository;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.service.BlogPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocalRepositoryAnalysisService {
    private static final int DEFAULT_COMMIT_LIMIT = 20;

    private final LocalRepositoryRepository localRepositoryRepository;
    private final LocalRepositoryAnalysisReportRepository reportRepository;
    private final LocalGitRepositoryScanner localGitRepositoryScanner;
    private final LocalOnlyRepositoryAnalyzer localOnlyRepositoryAnalyzer;
    private final OpenAiGitAnalysisClient openAiGitAnalysisClient;
    private final BlogPostService blogPostService;
    private final ObjectMapper objectMapper;

    @Transactional
    public LocalRepositoryResponse createRepository(CreateLocalRepositoryRequest request) {
        String localPath = localGitRepositoryScanner.normalizeRepositoryPath(request.localPath());
        if (localRepositoryRepository.existsByLocalPath(localPath)) {
            throw new GeneralException(ErrorCode.CONFLICT, "이미 등록된 로컬 저장소 경로입니다.");
        }

        LocalRepositoryEntity repository = localRepositoryRepository.save(LocalRepositoryEntity.builder()
                .name(request.name().trim())
                .localPath(localPath)
                .defaultBranch(defaultText(request.defaultBranch(), "main"))
                .description(trimToNull(request.description()))
                .active(request.active() == null || request.active())
                .build());
        return LocalRepositoryResponse.from(repository);
    }

    @Transactional(readOnly = true)
    public List<LocalRepositoryResponse> getRepositories() {
        return localRepositoryRepository.findAllByOrderByIdDesc()
                .stream()
                .map(LocalRepositoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocalRepositoryResponse getRepository(Long repositoryId) {
        return LocalRepositoryResponse.from(getRepositoryOrThrow(repositoryId));
    }

    @Transactional
    public LocalRepositoryResponse updateRepository(Long repositoryId, UpdateLocalRepositoryRequest request) {
        LocalRepositoryEntity repository = getRepositoryOrThrow(repositoryId);
        String localPath = localGitRepositoryScanner.normalizeRepositoryPath(request.localPath());
        if (localRepositoryRepository.existsByLocalPathAndIdNot(localPath, repositoryId)) {
            throw new GeneralException(ErrorCode.CONFLICT, "이미 등록된 로컬 저장소 경로입니다.");
        }

        repository.update(
                request.name().trim(),
                localPath,
                defaultText(request.defaultBranch(), "main"),
                trimToNull(request.description()),
                request.active() == null || request.active()
        );
        return LocalRepositoryResponse.from(repository);
    }

    @Transactional(noRollbackFor = GeneralException.class)
    public LocalRepositoryAnalysisReportResponse analyze(Long repositoryId, AnalyzeLocalRepositoryRequest request) {
        LocalRepositoryEntity repository = getRepositoryOrThrow(repositoryId);
        if (!repository.isActive()) {
            throw new GeneralException(ErrorCode.CONFLICT, "비활성화된 로컬 저장소는 분석할 수 없습니다.");
        }

        int commitLimit = request.commitLimit() == null ? DEFAULT_COMMIT_LIMIT : request.commitLimit();
        boolean includeUncommitted = request.includeUncommittedChanges() == null || request.includeUncommittedChanges();
        LocalRepositoryAnalysisMode mode = request.analysisMode() == null
                ? LocalRepositoryAnalysisMode.LOCAL_ONLY
                : request.analysisMode();
        String focus = trimToNull(request.focus());
        String sourceSummary = null;

        try {
            LocalGitSnapshot snapshot = localGitRepositoryScanner.scan(repository, commitLimit, includeUncommitted);
            sourceSummary = snapshot.sourceSummary();

            AnalysisPayload payload = analyzeByMode(repository, snapshot, mode, focus);
            Long blogPostId = null;
            if (Boolean.TRUE.equals(request.createBlogPost())) {
                BlogPostResponse blogPost = blogPostService.createDraft(new CreateBlogPostRequest(
                        choose(payload.recommendedTitle(), repository.getName() + " 구현 기록 정리"),
                        null,
                        payload.analysisSummary(),
                        payload.draftMarkdown(),
                        payload.keywords(),
                        "로컬 Git 저장소 " + repository.getLocalPath() + " 기반 분석 결과"
                ));
                blogPostId = blogPost.id();
            }

            LocalRepositoryAnalysisReportEntity report = reportRepository.save(LocalRepositoryAnalysisReportEntity.builder()
                    .localRepository(repository)
                    .status(GitAnalysisStatus.SUCCESS)
                    .analysisMode(mode)
                    .commitLimit(commitLimit)
                    .includeUncommittedChanges(includeUncommitted)
                    .focus(focus)
                    .sourceSummary(sourceSummary)
                    .analysisSummary(payload.analysisSummary())
                    .keywordsJson(writeJson(payload.keywords()))
                    .topicCandidatesJson(writeJson(payload.topicCandidates()))
                    .recommendedTitle(payload.recommendedTitle())
                    .draftMarkdown(payload.draftMarkdown())
                    .rawResponse(payload.rawResponse())
                    .modelName(payload.modelName())
                    .createdBlogPostId(blogPostId)
                    .build());
            return LocalRepositoryAnalysisReportResponse.from(report, objectMapper);
        } catch (GeneralException exception) {
            saveFailureReport(repository, mode, commitLimit, includeUncommitted, focus, sourceSummary, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            saveFailureReport(repository, mode, commitLimit, includeUncommitted, focus, sourceSummary, exception.getMessage());
            throw new GeneralException(ErrorCode.BAD_REQUEST, "로컬 Git 저장소 분석 중 오류가 발생했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<LocalRepositoryAnalysisReportResponse> getReports(Long repositoryId) {
        getRepositoryOrThrow(repositoryId);
        return reportRepository.findAllByLocalRepositoryIdOrderByIdDesc(repositoryId)
                .stream()
                .map(report -> LocalRepositoryAnalysisReportResponse.from(report, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public LocalRepositoryAnalysisReportResponse getReport(Long reportId) {
        return LocalRepositoryAnalysisReportResponse.from(getReportOrThrow(reportId), objectMapper);
    }

    @Transactional
    public BlogPostResponse createBlogPostFromReport(Long reportId) {
        LocalRepositoryAnalysisReportEntity report = getReportOrThrow(reportId);
        if (report.getStatus() != GitAnalysisStatus.SUCCESS) {
            throw new GeneralException(ErrorCode.CONFLICT, "성공한 분석 결과만 블로그 초안으로 전환할 수 있습니다.");
        }
        if (report.getCreatedBlogPostId() != null) {
            return blogPostService.getBlogPost(report.getCreatedBlogPostId());
        }

        BlogPostResponse blogPost = blogPostService.createDraft(new CreateBlogPostRequest(
                choose(report.getRecommendedTitle(), report.getLocalRepository().getName() + " 구현 기록 정리"),
                null,
                report.getAnalysisSummary(),
                report.getDraftMarkdown(),
                readStringList(report.getKeywordsJson()),
                "로컬 Git 저장소 " + report.getLocalRepository().getLocalPath() + " 기반 분석 결과"
        ));
        report.connectBlogPost(blogPost.id());
        return blogPost;
    }

    private AnalysisPayload analyzeByMode(
            LocalRepositoryEntity repository,
            LocalGitSnapshot snapshot,
            LocalRepositoryAnalysisMode mode,
            String focus
    ) {
        if (mode == LocalRepositoryAnalysisMode.OPENAI) {
            OpenAiGitAnalysisResult result = openAiGitAnalysisClient.analyze(
                    "local:" + repository.getName(),
                    snapshot.branchName(),
                    focus,
                    snapshot.sourceSummary()
            );
            return new AnalysisPayload(
                    result.analysisSummary(),
                    result.keywords(),
                    result.topicCandidates(),
                    result.recommendedTitle(),
                    result.draftMarkdown(),
                    result.rawResponse(),
                    result.modelName()
            );
        }

        LocalOnlyAnalysisResult result = localOnlyRepositoryAnalyzer.analyze(repository, focus, snapshot.sourceSummary());
        return new AnalysisPayload(
                result.analysisSummary(),
                result.keywords(),
                result.topicCandidates(),
                result.recommendedTitle(),
                result.draftMarkdown(),
                null,
                "LOCAL_ONLY"
        );
    }

    private LocalRepositoryEntity getRepositoryOrThrow(Long repositoryId) {
        return localRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "로컬 저장소를 찾을 수 없습니다."));
    }

    private LocalRepositoryAnalysisReportEntity getReportOrThrow(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "로컬 저장소 분석 결과를 찾을 수 없습니다."));
    }

    private void saveFailureReport(
            LocalRepositoryEntity repository,
            LocalRepositoryAnalysisMode mode,
            int commitLimit,
            boolean includeUncommitted,
            String focus,
            String sourceSummary,
            String errorMessage
    ) {
        reportRepository.save(LocalRepositoryAnalysisReportEntity.builder()
                .localRepository(repository)
                .status(GitAnalysisStatus.FAILED)
                .analysisMode(mode)
                .commitLimit(commitLimit)
                .includeUncommittedChanges(includeUncommitted)
                .focus(focus)
                .sourceSummary(sourceSummary)
                .keywordsJson("[]")
                .topicCandidatesJson("[]")
                .errorMessage(errorMessage == null ? "알 수 없는 오류" : truncate(errorMessage, 2000))
                .build());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "분석 결과를 JSON으로 저장할 수 없습니다.");
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String choose(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
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

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record AnalysisPayload(
            String analysisSummary,
            List<String> keywords,
            List<com.miso.blog.git.dto.TopicCandidateResponse> topicCandidates,
            String recommendedTitle,
            String draftMarkdown,
            String rawResponse,
            String modelName
    ) {
    }
}
