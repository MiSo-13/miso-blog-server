package com.miso.blog.localrepo.service;

import com.miso.blog.git.dto.TopicCandidateResponse;
import com.miso.blog.localrepo.dto.LocalOnlyAnalysisResult;
import com.miso.blog.localrepo.entity.LocalRepositoryEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LocalOnlyRepositoryAnalyzer {
    private static final Pattern DIFF_FILE_PATTERN = Pattern.compile("(?m)^diff --git a/([^\\s]+) b/([^\\s]+)");
    private static final Pattern COMMIT_MESSAGE_PATTERN = Pattern.compile("(?m)^[a-f0-9]{7,40} \\| [^|]+ \\| [^|]+ \\| (.+)$");

    public LocalOnlyAnalysisResult analyze(LocalRepositoryEntity repository, String focus, String sourceSummary) {
        List<String> sourceFiles = extractSourceFiles(sourceSummary);
        List<String> commitMessages = extractCommitMessages(sourceSummary);
        List<String> keywords = extractKeywords(sourceSummary, sourceFiles);
        List<TopicCandidateResponse> topics = buildTopics(repository, sourceFiles, commitMessages, keywords);
        String recommendedTitle = topics.isEmpty()
                ? repository.getName() + " 구현 기록 정리"
                : topics.get(0).title();
        String analysisSummary = buildAnalysisSummary(repository, focus, sourceFiles, commitMessages, keywords);
        String draftMarkdown = buildDraftMarkdown(repository, analysisSummary, topics, sourceFiles, commitMessages, keywords);

        return new LocalOnlyAnalysisResult(
                analysisSummary,
                keywords,
                topics,
                recommendedTitle,
                draftMarkdown
        );
    }

    private List<String> extractSourceFiles(String sourceSummary) {
        LinkedHashSet<String> files = new LinkedHashSet<>();
        Matcher matcher = DIFF_FILE_PATTERN.matcher(sourceSummary);
        while (matcher.find()) {
            files.add(matcher.group(2));
            if (files.size() >= 30) {
                break;
            }
        }
        return new ArrayList<>(files);
    }

    private List<String> extractCommitMessages(String sourceSummary) {
        LinkedHashSet<String> messages = new LinkedHashSet<>();
        Matcher matcher = COMMIT_MESSAGE_PATTERN.matcher(sourceSummary);
        while (matcher.find()) {
            String message = matcher.group(1).trim();
            if (!message.isBlank()) {
                messages.add(message);
            }
            if (messages.size() >= 20) {
                break;
            }
        }
        return new ArrayList<>(messages);
    }

    private List<String> extractKeywords(String sourceSummary, List<String> sourceFiles) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        String lower = sourceSummary.toLowerCase(Locale.ROOT);

        addIfContains(keywords, lower, "spring", "Spring Boot");
        addIfContains(keywords, lower, "controller", "REST API");
        addIfContains(keywords, lower, "repository", "Repository");
        addIfContains(keywords, lower, "entity", "JPA Entity");
        addIfContains(keywords, lower, "transactional", "Transaction");
        addIfContains(keywords, lower, "openai", "OpenAI");
        addIfContains(keywords, lower, "github", "GitHub");
        addIfContains(keywords, lower, "security", "Security");
        addIfContains(keywords, lower, "rabbit", "RabbitMQ");
        addIfContains(keywords, lower, "docker", "Docker");
        addIfContains(keywords, lower, "test", "Test");
        addIfContains(keywords, lower, "gradle", "Gradle");
        addIfContains(keywords, lower, "exception", "Exception Handling");
        addIfContains(keywords, lower, "swagger", "Swagger");
        addIfContains(keywords, lower, "markdown", "Markdown");

        for (String file : sourceFiles) {
            if (file.endsWith(".java")) {
                keywords.add("Java");
            }
            if (file.endsWith(".yml") || file.endsWith(".yaml")) {
                keywords.add("Configuration");
            }
            if (file.contains("/docs/") || file.startsWith("docs/")) {
                keywords.add("Documentation");
            }
        }

        if (keywords.isEmpty()) {
            keywords.add("Git");
            keywords.add("개발 기록");
        }
        return new ArrayList<>(keywords).subList(0, Math.min(keywords.size(), 15));
    }

    private List<TopicCandidateResponse> buildTopics(
            LocalRepositoryEntity repository,
            List<String> sourceFiles,
            List<String> commitMessages,
            List<String> keywords
    ) {
        List<String> files = sourceFiles.isEmpty() ? List.of(repository.getLocalPath()) : sourceFiles.subList(0, Math.min(sourceFiles.size(), 8));
        List<String> tags = keywords.subList(0, Math.min(keywords.size(), 5));

        List<TopicCandidateResponse> topics = new ArrayList<>();
        topics.add(new TopicCandidateResponse(
                repository.getName() + " 최근 구현 흐름을 코드 변경으로 되짚기",
                "최근 커밋을 기준으로 어떤 기능이 추가되고 구조가 어떻게 변했는지 정리",
                "commit message와 변경 파일이 실제 구현 흐름을 보여주므로 회고형 기술 글로 만들기 좋습니다.",
                files,
                tags
        ));
        topics.add(new TopicCandidateResponse(
                "작은 서버 기능을 도메인과 API로 나누어 확장한 과정",
                "도메인, DTO, 서비스, 컨트롤러 단위로 기능을 쌓아간 설계 설명",
                "변경 파일 경로에서 계층별 역할이 드러나면 독자가 따라가기 쉬운 설계 글이 됩니다.",
                files,
                tags
        ));
        topics.add(new TopicCandidateResponse(
                "구현하면서 드러난 설정과 운영 포인트 정리",
                "설정 파일, 테스트, 문서, 운영 API를 연결해 실전적인 구축 과정을 설명",
                "개인 프로젝트라도 운영 가능한 형태로 다듬는 과정은 개발 블로그 소재로 좋습니다.",
                files,
                tags
        ));
        topics.add(new TopicCandidateResponse(
                "커밋 메시지로 읽는 트러블슈팅 후보",
                "fix, modify, config, test 관련 커밋을 중심으로 문제 해결 과정을 재구성",
                "명확한 장애 로그가 없어도 커밋 흐름에서 시행착오와 수정 이유를 추적할 수 있습니다.",
                files,
                tags
        ));
        topics.add(new TopicCandidateResponse(
                "내 코드베이스를 블로그 글감으로 바꾸는 자동화 설계",
                "로컬 Git 분석 결과를 글감 후보와 Markdown 초안으로 전환하는 흐름 설명",
                "이 프로젝트의 핵심 가치와 직접 연결되는 메타 기술 글로 확장하기 좋습니다.",
                files,
                tags
        ));

        if (!commitMessages.isEmpty()) {
            topics.add(new TopicCandidateResponse(
                    "최근 커밋 " + commitMessages.size() + "개에서 뽑은 구현 포인트",
                    "커밋 단위로 구현 의도와 변화량을 정리",
                    "실제 커밋 메시지가 있으므로 구체적인 작업 기록형 글로 만들 수 있습니다.",
                    files,
                    tags
            ));
        }

        return topics;
    }

    private String buildAnalysisSummary(
            LocalRepositoryEntity repository,
            String focus,
            List<String> sourceFiles,
            List<String> commitMessages,
            List<String> keywords
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(repository.getName()).append(" 저장소의 최근 Git 기록을 로컬에서 분석했습니다. ");
        builder.append("최근 커밋 ").append(commitMessages.size()).append("개와 변경 파일 ").append(sourceFiles.size()).append("개를 기준으로 ");
        builder.append("블로그화할 수 있는 구현 포인트를 추렸습니다.");
        if (focus != null && !focus.isBlank()) {
            builder.append(" 분석 초점은 '").append(focus).append("'입니다.");
        }
        builder.append(" 주요 키워드는 ").append(String.join(", ", keywords.subList(0, Math.min(keywords.size(), 8)))).append("입니다.");
        return builder.toString();
    }

    private String buildDraftMarkdown(
            LocalRepositoryEntity repository,
            String analysisSummary,
            List<TopicCandidateResponse> topics,
            List<String> sourceFiles,
            List<String> commitMessages,
            List<String> keywords
    ) {
        String title = topics.isEmpty() ? repository.getName() + " 구현 기록 정리" : topics.get(0).title();
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("## 분석 요약\n\n");
        builder.append(analysisSummary).append("\n\n");
        builder.append("## 최근 커밋에서 보이는 흐름\n\n");
        if (commitMessages.isEmpty()) {
            builder.append("- 아직 커밋 메시지를 충분히 추출하지 못했습니다.\n");
        } else {
            for (String message : commitMessages.subList(0, Math.min(commitMessages.size(), 8))) {
                builder.append("- ").append(message).append('\n');
            }
        }
        builder.append("\n## 글감 후보\n\n");
        for (TopicCandidateResponse topic : topics.subList(0, Math.min(topics.size(), 5))) {
            builder.append("### ").append(topic.title()).append("\n\n");
            builder.append("- 관점: ").append(topic.angle()).append('\n');
            builder.append("- 이유: ").append(topic.reason()).append("\n\n");
        }
        builder.append("## 근거 파일\n\n");
        if (sourceFiles.isEmpty()) {
            builder.append("- 변경 파일을 추출하지 못했습니다.\n");
        } else {
            for (String file : sourceFiles.subList(0, Math.min(sourceFiles.size(), 12))) {
                builder.append("- `").append(file).append("`\n");
            }
        }
        builder.append("\n## 키워드\n\n");
        builder.append(String.join(", ", keywords)).append('\n');
        return builder.toString();
    }

    private void addIfContains(LinkedHashSet<String> keywords, String source, String needle, String keyword) {
        if (source.contains(needle)) {
            keywords.add(keyword);
        }
    }
}
