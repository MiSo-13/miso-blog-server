package com.miso.blog.git.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.common.security.SecretMaskingService;
import com.miso.blog.git.code.GitAnalysisStatus;
import com.miso.blog.git.dto.AnalyzeGitRepositoryRequest;
import com.miso.blog.git.dto.CreateGitRepositoryRequest;
import com.miso.blog.git.dto.GitAnalysisReportResponse;
import com.miso.blog.git.dto.GitRepositoryResponse;
import com.miso.blog.git.dto.GitRepositoryUpdateRequest;
import com.miso.blog.git.dto.OpenAiGitAnalysisResult;
import com.miso.blog.git.dto.RepositoryCommitSnapshot;
import com.miso.blog.git.dto.RepositoryFilePatch;
import com.miso.blog.git.entity.GitAnalysisReportEntity;
import com.miso.blog.git.entity.GitRepositoryEntity;
import com.miso.blog.git.repository.GitAnalysisReportRepository;
import com.miso.blog.git.repository.GitRepositoryRepository;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.code.BlogWritingMode;
import com.miso.blog.post.dto.CreateBlogPostFromAnalysisRequest;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.dto.GeneratedBlogDraft;
import com.miso.blog.post.service.LocalBlogDraftComposer;
import com.miso.blog.post.service.OpenAiBlogDraftComposer;
import com.miso.blog.post.service.BlogPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitRepositoryAnalysisService {
    private static final int DEFAULT_COMMIT_LIMIT = 10;
    private static final int SOURCE_SUMMARY_LIMIT = 45000;
    private static final int DEEP_SOURCE_SUMMARY_LIMIT = 90000;
    private static final int CODE_CONTEXT_FILE_LIMIT = 12;
    private static final int CODE_CONTEXT_PER_FILE_LIMIT = 6000;

    private final GitRepositoryRepository gitRepositoryRepository;
    private final GitAnalysisReportRepository gitAnalysisReportRepository;
    private final GitHubRepositoryClient gitHubRepositoryClient;
    private final OpenAiGitAnalysisClient openAiGitAnalysisClient;
    private final BlogPostService blogPostService;
    private final LocalBlogDraftComposer localBlogDraftComposer;
    private final OpenAiBlogDraftComposer openAiBlogDraftComposer;
    private final SecretMaskingService secretMaskingService;
    private final ObjectMapper objectMapper;

    @Value("${blog.github.analysis.max-all-commits:300}")
    private int maxAllCommitLimit;

    @Transactional
    public GitRepositoryResponse createRepository(CreateGitRepositoryRequest request) {
        String repositoryFullName = request.repositoryFullName().trim();
        if (gitRepositoryRepository.existsByRepositoryFullName(repositoryFullName)) {
            throw new GeneralException(ErrorCode.CONFLICT, "이미 등록된 Git 저장소입니다.");
        }

        GitRepositoryEntity repository = gitRepositoryRepository.save(GitRepositoryEntity.builder()
                .repositoryFullName(repositoryFullName)
                .defaultBranch(defaultText(request.defaultBranch(), "main"))
                .description(trimToNull(request.description()))
                .active(request.active() == null || request.active())
                .build());
        return GitRepositoryResponse.from(repository);
    }

    @Transactional(readOnly = true)
    public List<GitRepositoryResponse> getRepositories() {
        return gitRepositoryRepository.findAllByOrderByIdDesc()
                .stream()
                .map(GitRepositoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GitRepositoryResponse getRepository(Long repositoryId) {
        return GitRepositoryResponse.from(getRepositoryOrThrow(repositoryId));
    }

    @Transactional
    public GitRepositoryResponse updateRepository(Long repositoryId, GitRepositoryUpdateRequest request) {
        GitRepositoryEntity repository = getRepositoryOrThrow(repositoryId);
        String repositoryFullName = request.repositoryFullName().trim();
        if (gitRepositoryRepository.existsByRepositoryFullNameAndIdNot(repositoryFullName, repositoryId)) {
            throw new GeneralException(ErrorCode.CONFLICT, "이미 등록된 Git 저장소입니다.");
        }

        repository.update(
                repositoryFullName,
                defaultText(request.defaultBranch(), "main"),
                trimToNull(request.description()),
                request.active() == null || request.active()
        );
        return GitRepositoryResponse.from(repository);
    }

    @Transactional(noRollbackFor = GeneralException.class)
    public GitAnalysisReportResponse analyze(Long repositoryId, AnalyzeGitRepositoryRequest request) {
        GitRepositoryEntity repository = getRepositoryOrThrow(repositoryId);
        if (!repository.isActive()) {
            throw new GeneralException(ErrorCode.CONFLICT, "비활성화된 저장소는 분석할 수 없습니다.");
        }

        int commitLimit = resolveCommitLimit(request);
        String focus = trimToNull(request.focus());
        String sourceSummary = null;

        try {
            List<RepositoryCommitSnapshot> commits = gitHubRepositoryClient.fetchRecentCommits(
                    repository.getRepositoryFullName(),
                    repository.getDefaultBranch(),
                    commitLimit
            );
            commitLimit = commits.size();
            sourceSummary = secretMaskingService.mask(buildSourceSummary(repository, commits, isDeepAnalysis(request)));
            OpenAiGitAnalysisResult result = openAiGitAnalysisClient.analyze(
                    repository.getRepositoryFullName(),
                    repository.getDefaultBranch(),
                    focus,
                    sourceSummary
            );

            Long blogPostId = null;
            if (Boolean.TRUE.equals(request.createBlogPost())) {
                BlogPostResponse blogPost = blogPostService.createDraft(new CreateBlogPostRequest(
                        choose(result.recommendedTitle(), "Git 구현 기록 분석"),
                        null,
                        result.analysisSummary(),
                        result.draftMarkdown(),
                        result.keywords(),
                        "Git 저장소 " + repository.getRepositoryFullName() + " 최근 commit 기반 AI 분석 결과"
                ));
                blogPostId = blogPost.id();
            }

            GitAnalysisReportEntity report = gitAnalysisReportRepository.save(GitAnalysisReportEntity.builder()
                    .repository(repository)
                    .status(GitAnalysisStatus.SUCCESS)
                    .commitLimit(commitLimit)
                    .focus(focus)
                    .sourceSummary(sourceSummary)
                    .analysisSummary(result.analysisSummary())
                    .keywordsJson(writeJson(result.keywords()))
                    .topicCandidatesJson(writeJson(result.topicCandidates()))
                    .recommendedTitle(result.recommendedTitle())
                    .draftMarkdown(result.draftMarkdown())
                    .rawResponse(result.rawResponse())
                    .modelName(result.modelName())
                    .createdBlogPostId(blogPostId)
                    .build());
            return GitAnalysisReportResponse.from(report, objectMapper);
        } catch (GeneralException exception) {
            saveFailureReport(repository, commitLimit, focus, sourceSummary, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            saveFailureReport(repository, commitLimit, focus, sourceSummary, exception.getMessage());
            throw new GeneralException(ErrorCode.BAD_REQUEST, "Git 저장소 분석 중 오류가 발생했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<GitAnalysisReportResponse> getReports(Long repositoryId) {
        getRepositoryOrThrow(repositoryId);
        return gitAnalysisReportRepository.findAllByRepositoryIdOrderByIdDesc(repositoryId)
                .stream()
                .map(report -> GitAnalysisReportResponse.from(report, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public GitAnalysisReportResponse getReport(Long reportId) {
        return GitAnalysisReportResponse.from(getReportOrThrow(reportId), objectMapper);
    }

    @Transactional
    public void deleteReports(Long repositoryId) {
        getRepositoryOrThrow(repositoryId);
        gitAnalysisReportRepository.deleteAll(gitAnalysisReportRepository.findAllByRepositoryIdOrderByIdDesc(repositoryId));
    }

    @Transactional
    public void deleteReport(Long reportId) {
        gitAnalysisReportRepository.delete(getReportOrThrow(reportId));
    }

    @Transactional
    public BlogPostResponse createBlogPostFromReport(Long reportId) {
        GitAnalysisReportEntity report = getReportOrThrow(reportId);
        if (report.getStatus() != GitAnalysisStatus.SUCCESS) {
            throw new GeneralException(ErrorCode.CONFLICT, "성공한 분석 결과만 블로그 초안으로 전환할 수 있습니다.");
        }
        if (report.getCreatedBlogPostId() != null) {
            return blogPostService.getBlogPost(report.getCreatedBlogPostId());
        }

        List<String> tags = readStringList(report.getKeywordsJson());
        BlogPostResponse blogPost = blogPostService.createDraft(new CreateBlogPostRequest(
                choose(report.getRecommendedTitle(), "Git 구현 기록 분석"),
                null,
                report.getAnalysisSummary(),
                report.getDraftMarkdown(),
                tags,
                "Git 저장소 " + report.getRepository().getRepositoryFullName() + " 최근 commit 기반 AI 분석 결과"
        ));

        report.connectBlogPost(blogPost.id());
        return blogPost;
    }

    @Transactional
    public BlogPostResponse writeBlogPostFromReport(Long reportId, CreateBlogPostFromAnalysisRequest request) {
        GitAnalysisReportEntity report = getReportOrThrow(reportId);
        if (report.getStatus() != GitAnalysisStatus.SUCCESS) {
            throw new GeneralException(ErrorCode.CONFLICT, "성공한 분석 결과만 블로그 글로 작성할 수 있습니다.");
        }

        BlogWritingMode writingMode = request.writingMode() == null ? BlogWritingMode.LOCAL_ONLY : request.writingMode();
        List<String> keywords = mergeSelectedKeywords(request.selectedKeywords(), readStringList(report.getKeywordsJson()));
        List<com.miso.blog.git.dto.TopicCandidateResponse> topics = readTopicList(report.getTopicCandidatesJson());
        GeneratedBlogDraft draft = writingMode == BlogWritingMode.OPENAI
                ? openAiBlogDraftComposer.compose(
                        report.getRepository().getRepositoryFullName(),
                        report.getAnalysisSummary(),
                        report.getSourceSummary(),
                        keywords,
                        topics,
                        request
                )
                : localBlogDraftComposer.compose(
                        report.getRepository().getRepositoryFullName(),
                        report.getAnalysisSummary(),
                        report.getSourceSummary(),
                        keywords,
                        topics,
                        request
                );

        BlogPostResponse blogPost = blogPostService.createDraftAndMaybeReviewReady(new CreateBlogPostRequest(
                draft.title(),
                null,
                draft.summary(),
                draft.contentMarkdown(),
                draft.tags(),
                "선택 키워드 기반 GitHub 분석 글 작성 결과. reportId=" + report.getId()
        ), Boolean.TRUE.equals(request.markReviewReady()));
        report.connectBlogPost(blogPost.id());
        return blogPost;
    }

    private GitRepositoryEntity getRepositoryOrThrow(Long repositoryId) {
        return gitRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "Git 저장소를 찾을 수 없습니다."));
    }

    private GitAnalysisReportEntity getReportOrThrow(Long reportId) {
        return gitAnalysisReportRepository.findById(reportId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "Git 분석 결과를 찾을 수 없습니다."));
    }

    private void saveFailureReport(
            GitRepositoryEntity repository,
            int commitLimit,
            String focus,
            String sourceSummary,
            String errorMessage
    ) {
        gitAnalysisReportRepository.save(GitAnalysisReportEntity.builder()
                .repository(repository)
                .status(GitAnalysisStatus.FAILED)
                .commitLimit(commitLimit)
                .focus(focus)
                .sourceSummary(sourceSummary)
                .keywordsJson("[]")
                .topicCandidatesJson("[]")
                .errorMessage(errorMessage == null ? "알 수 없는 오류" : truncate(errorMessage, 2000))
                .build());
    }

    private String buildSourceSummary(GitRepositoryEntity repository, List<RepositoryCommitSnapshot> commits, boolean deepAnalysis) {
        int sourceSummaryLimit = deepAnalysis ? DEEP_SOURCE_SUMMARY_LIMIT : SOURCE_SUMMARY_LIMIT;
        StringBuilder builder = new StringBuilder();
        builder.append("repository: ").append(repository.getRepositoryFullName()).append('\n');
        builder.append("branch: ").append(repository.getDefaultBranch()).append('\n');
        builder.append("commitCount: ").append(commits.size()).append("\n\n");
        builder.append("deepAnalysis: ").append(deepAnalysis).append("\n\n");

        LinkedHashSet<String> changedSourceFiles = new LinkedHashSet<>();

        for (RepositoryCommitSnapshot commit : commits) {
            builder.append("## commit ").append(commit.sha()).append('\n');
            builder.append("- message: ").append(defaultText(commit.message(), "(message 없음)")).append('\n');
            builder.append("- author: ").append(defaultText(commit.authorName(), "(author 없음)")).append('\n');
            builder.append("- committedAt: ").append(commit.committedAt()).append('\n');
            builder.append("- files:\n");

            for (RepositoryFilePatch file : commit.files()) {
                builder.append("  - ").append(file.filename())
                        .append(" [").append(file.status()).append("]")
                        .append(" +").append(file.additions() == null ? 0 : file.additions())
                        .append(" -").append(file.deletions() == null ? 0 : file.deletions())
                        .append('\n');
                if (file.patch() != null && !file.patch().isBlank()) {
                    builder.append("```diff\n").append(file.patch()).append("\n```\n");
                }
                if (deepAnalysis && isReadableSourceFile(file.filename())) {
                    changedSourceFiles.add(file.filename());
                }

                if (builder.length() >= sourceSummaryLimit) {
                    builder.append("\n... source summary truncated ...");
                    return builder.toString();
                }
            }
            builder.append('\n');
        }

        if (deepAnalysis && !changedSourceFiles.isEmpty()) {
            appendDeepCodeContext(builder, repository, changedSourceFiles, sourceSummaryLimit);
        }

        return truncate(builder.toString(), sourceSummaryLimit);
    }

    private void appendDeepCodeContext(
            StringBuilder builder,
            GitRepositoryEntity repository,
            LinkedHashSet<String> changedSourceFiles,
            int sourceSummaryLimit
    ) {
        builder.append("\n## current source context for deeper analysis\n");
        builder.append("The following snippets are current branch file contents for files touched by recent commits. Use them to infer structure and implementation intent without copying long code blocks into the blog.\n\n");

        int appendedCount = 0;
        for (String filePath : changedSourceFiles) {
            if (appendedCount >= CODE_CONTEXT_FILE_LIMIT || builder.length() >= sourceSummaryLimit) {
                break;
            }
            String content = gitHubRepositoryClient.fetchFileContent(
                    repository.getRepositoryFullName(),
                    repository.getDefaultBranch(),
                    filePath,
                    CODE_CONTEXT_PER_FILE_LIMIT
            );
            if (content == null || content.isBlank()) {
                continue;
            }
            builder.append("### file ").append(filePath).append('\n');
            builder.append("```").append(languageHint(filePath)).append('\n')
                    .append(content)
                    .append("\n```\n\n");
            appendedCount++;
        }
    }

    private int resolveCommitLimit(AnalyzeGitRepositoryRequest request) {
        if (Boolean.TRUE.equals(request.analyzeAllCommits())) {
            return Math.max(1, maxAllCommitLimit);
        }
        return request.commitLimit() == null ? DEFAULT_COMMIT_LIMIT : request.commitLimit();
    }

    private boolean isDeepAnalysis(AnalyzeGitRepositoryRequest request) {
        return request.deepAnalysis() == null || request.deepAnalysis();
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

    private List<com.miso.blog.git.dto.TopicCandidateResponse> readTopicList(String json) {
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

    private List<String> mergeSelectedKeywords(List<String> selectedKeywords, List<String> fallbackKeywords) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (selectedKeywords != null) {
            selectedKeywords.stream()
                    .map(this::trimToNull)
                    .filter(value -> value != null)
                    .forEach(values::add);
        }
        if (values.isEmpty() && fallbackKeywords != null) {
            fallbackKeywords.stream()
                    .map(this::trimToNull)
                    .filter(value -> value != null)
                    .limit(6)
                    .forEach(values::add);
        }
        return new ArrayList<>(values);
    }

    private boolean isReadableSourceFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String lower = filePath.toLowerCase();
        if (lower.contains("application-private") || lower.endsWith(".lock") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".ico")
                || lower.endsWith(".pdf") || lower.endsWith(".zip") || lower.endsWith(".jar")) {
            return false;
        }
        return lower.endsWith(".java")
                || lower.endsWith(".kt")
                || lower.endsWith(".js")
                || lower.endsWith(".ts")
                || lower.endsWith(".tsx")
                || lower.endsWith(".jsx")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".properties")
                || lower.endsWith(".gradle")
                || lower.endsWith(".md")
                || lower.endsWith(".json")
                || lower.endsWith(".sql")
                || lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".scss");
    }

    private String languageHint(String filePath) {
        if (filePath == null) {
            return "";
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".kt")) {
            return "kotlin";
        }
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) {
            return "javascript";
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) {
            return "typescript";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "yaml";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".sql")) {
            return "sql";
        }
        if (lower.endsWith(".html")) {
            return "html";
        }
        if (lower.endsWith(".css") || lower.endsWith(".scss")) {
            return "css";
        }
        return "";
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
}
