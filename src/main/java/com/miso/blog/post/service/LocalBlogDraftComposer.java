package com.miso.blog.post.service;

import com.miso.blog.git.dto.TopicCandidateResponse;
import com.miso.blog.post.dto.CreateBlogPostFromAnalysisRequest;
import com.miso.blog.post.dto.GeneratedBlogDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class LocalBlogDraftComposer {
    public GeneratedBlogDraft compose(
            String repositoryName,
            String analysisSummary,
            String sourceSummary,
            List<String> analysisKeywords,
            List<TopicCandidateResponse> topicCandidates,
            CreateBlogPostFromAnalysisRequest request
    ) {
        List<String> selectedKeywords = normalizeSelectedKeywords(request.selectedKeywords(), analysisKeywords);
        TopicCandidateResponse selectedTopic = selectTopic(topicCandidates, request.selectedTopicTitle(), selectedKeywords);
        String title = selectedTopic == null
                ? repositoryName + " 구현 기록에서 뽑은 개발 블로그"
                : selectedTopic.title();
        String summary = buildSummary(analysisSummary, selectedKeywords, request.writingFocus());
        String content = buildMarkdown(
                title,
                summary,
                selectedKeywords,
                selectedTopic,
                topicCandidates,
                sourceSummary,
                request
        );
        List<String> tags = selectedTopic == null || selectedTopic.tags() == null || selectedTopic.tags().isEmpty()
                ? selectedKeywords
                : mergeTags(selectedKeywords, selectedTopic.tags());

        return new GeneratedBlogDraft(
                title,
                summary,
                content,
                tags,
                null,
                "LOCAL_ONLY"
        );
    }

    private String buildSummary(String analysisSummary, List<String> selectedKeywords, String writingFocus) {
        StringBuilder builder = new StringBuilder();
        builder.append(analysisSummary == null || analysisSummary.isBlank()
                ? "선택한 키워드를 중심으로 로컬 Git 분석 결과를 블로그 초안으로 재구성했습니다."
                : analysisSummary);
        if (!selectedKeywords.isEmpty()) {
            builder.append(" 핵심 키워드는 ").append(String.join(", ", selectedKeywords)).append("입니다.");
        }
        if (writingFocus != null && !writingFocus.isBlank()) {
            builder.append(" 작성 초점은 '").append(writingFocus.trim()).append("'입니다.");
        }
        return builder.toString();
    }

    private String buildMarkdown(
            String title,
            String summary,
            List<String> selectedKeywords,
            TopicCandidateResponse selectedTopic,
            List<TopicCandidateResponse> topicCandidates,
            String sourceSummary,
            CreateBlogPostFromAnalysisRequest request
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("## 왜 이 주제를 글로 남기나\n\n");
        builder.append(summary).append("\n\n");
        if (request.audience() != null && !request.audience().isBlank()) {
            builder.append("이 글은 ").append(request.audience().trim()).append("를 독자로 상정하고 작성했습니다.\n\n");
        }

        builder.append("## 선택한 키워드\n\n");
        for (String keyword : selectedKeywords) {
            builder.append("- ").append(keyword).append('\n');
        }

        builder.append("\n## 글의 관점\n\n");
        if (selectedTopic != null) {
            builder.append("- 제목 후보: ").append(selectedTopic.title()).append('\n');
            builder.append("- 관점: ").append(defaultText(selectedTopic.angle(), "구현 흐름 중심")).append('\n');
            builder.append("- 이유: ").append(defaultText(selectedTopic.reason(), "최근 변경사항과 연결되는 주제입니다.")).append("\n\n");
        } else {
            builder.append("선택한 키워드를 기준으로 최근 구현 흐름을 정리합니다.\n\n");
        }

        builder.append("## 구현 흐름 정리\n\n");
        appendSourceHighlights(builder, sourceSummary);

        builder.append("\n## 블로그에서 더 풀어볼 포인트\n\n");
        for (TopicCandidateResponse topic : topicCandidates.subList(0, Math.min(topicCandidates.size(), 5))) {
            builder.append("- ").append(topic.title()).append(": ").append(defaultText(topic.reason(), "구현 맥락을 설명하기 좋습니다.")).append('\n');
        }

        builder.append("\n## 마무리\n\n");
        builder.append("이 초안은 로컬 Git 분석 결과를 바탕으로 만든 1차 글입니다. 실제 발행 전에는 코드 예시, 장애 상황, 의사결정 배경을 더 구체화하면 좋습니다.\n");
        return builder.toString();
    }

    private void appendSourceHighlights(StringBuilder builder, String sourceSummary) {
        if (sourceSummary == null || sourceSummary.isBlank()) {
            builder.append("아직 분석 근거가 충분하지 않습니다.\n");
            return;
        }

        List<String> lines = new ArrayList<>();
        for (String line : sourceSummary.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("diff --git")
                    || trimmed.startsWith("### changed files")
                    || trimmed.startsWith("### recent commit overview")
                    || trimmed.matches("^[a-f0-9]{7,40} \\| .+")) {
                lines.add(trimmed);
            }
            if (lines.size() >= 12) {
                break;
            }
        }

        if (lines.isEmpty()) {
            builder.append("source summary를 참고해 구현 흐름을 정리합니다.\n");
            return;
        }

        for (String line : lines) {
            builder.append("- `").append(line.replace("`", "'")).append("`\n");
        }
    }

    private List<String> normalizeSelectedKeywords(List<String> selectedKeywords, List<String> fallbackKeywords) {
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
                    .limit(5)
                    .forEach(values::add);
        }
        return new ArrayList<>(values);
    }

    private TopicCandidateResponse selectTopic(
            List<TopicCandidateResponse> topicCandidates,
            String selectedTopicTitle,
            List<String> selectedKeywords
    ) {
        if (topicCandidates == null || topicCandidates.isEmpty()) {
            return null;
        }
        if (selectedTopicTitle != null && !selectedTopicTitle.isBlank()) {
            for (TopicCandidateResponse topic : topicCandidates) {
                if (selectedTopicTitle.trim().equalsIgnoreCase(topic.title())) {
                    return topic;
                }
            }
        }
        for (TopicCandidateResponse topic : topicCandidates) {
            String source = (topic.title() + " " + topic.angle() + " " + topic.reason()).toLowerCase();
            for (String keyword : selectedKeywords) {
                if (source.contains(keyword.toLowerCase())) {
                    return topic;
                }
            }
        }
        return topicCandidates.get(0);
    }

    private List<String> mergeTags(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        first.forEach(values::add);
        second.stream()
                .map(this::trimToNull)
                .filter(value -> value != null)
                .forEach(values::add);
        return new ArrayList<>(values).subList(0, Math.min(values.size(), 10));
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
}
