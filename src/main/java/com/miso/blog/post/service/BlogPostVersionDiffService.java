package com.miso.blog.post.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.post.dto.BlogPostVersionDiffLineResponse;
import com.miso.blog.post.dto.BlogPostVersionDiffResponse;
import com.miso.blog.post.dto.BlogPostVersionDiffSectionResponse;
import com.miso.blog.post.dto.BlogPostVersionResponse;
import com.miso.blog.post.entity.BlogPostVersionEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import com.miso.blog.post.repository.BlogPostVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogPostVersionDiffService {
    private final BlogPostRepository blogPostRepository;
    private final BlogPostVersionRepository blogPostVersionRepository;

    @Transactional(readOnly = true)
    public BlogPostVersionDiffResponse diff(Long blogPostId, Integer fromVersionNo, Integer toVersionNo) {
        if (!blogPostRepository.existsById(blogPostId)) {
            throw new GeneralException(ErrorCode.NOT_FOUND, "블로그 글을 찾을 수 없습니다.");
        }

        List<BlogPostVersionEntity> versions = blogPostVersionRepository.findAllByBlogPostIdOrderByVersionNoAsc(blogPostId);
        if (versions.size() < 2 && (fromVersionNo == null || toVersionNo == null)) {
            throw new GeneralException(ErrorCode.CONFLICT, "비교할 블로그 글 버전이 부족합니다.");
        }

        int resolvedToVersionNo = toVersionNo == null ? versions.get(versions.size() - 1).getVersionNo() : toVersionNo;
        int resolvedFromVersionNo = fromVersionNo == null ? resolvedToVersionNo - 1 : fromVersionNo;
        if (resolvedFromVersionNo == resolvedToVersionNo) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "서로 다른 버전을 선택해야 합니다.");
        }

        BlogPostVersionEntity fromVersion = getVersion(blogPostId, resolvedFromVersionNo);
        BlogPostVersionEntity toVersion = getVersion(blogPostId, resolvedToVersionNo);
        List<BlogPostVersionDiffSectionResponse> sections = List.of(
                buildSection("title", fromVersion.getTitle(), toVersion.getTitle()),
                buildSection("summary", fromVersion.getSummary(), toVersion.getSummary()),
                buildSection("contentMarkdown", fromVersion.getContentMarkdown(), toVersion.getContentMarkdown()),
                buildSection("tagsJson", fromVersion.getTagsJson(), toVersion.getTagsJson())
        );

        int addedLineCount = sections.stream().mapToInt(BlogPostVersionDiffSectionResponse::addedLineCount).sum();
        int deletedLineCount = sections.stream().mapToInt(BlogPostVersionDiffSectionResponse::deletedLineCount).sum();

        return new BlogPostVersionDiffResponse(
                blogPostId,
                resolvedFromVersionNo,
                resolvedToVersionNo,
                addedLineCount,
                deletedLineCount,
                addedLineCount > 0 || deletedLineCount > 0,
                BlogPostVersionResponse.from(fromVersion),
                BlogPostVersionResponse.from(toVersion),
                sections
        );
    }

    private BlogPostVersionEntity getVersion(Long blogPostId, int versionNo) {
        return blogPostVersionRepository.findByBlogPostIdAndVersionNo(blogPostId, versionNo)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "블로그 글 버전을 찾을 수 없습니다."));
    }

    private BlogPostVersionDiffSectionResponse buildSection(String fieldName, String oldValue, String newValue) {
        List<String> oldLines = splitLines(oldValue);
        List<String> newLines = splitLines(newValue);
        List<BlogPostVersionDiffLineResponse> lines = buildLineDiff(oldLines, newLines);
        int added = (int) lines.stream().filter(line -> "INSERT".equals(line.type())).count();
        int deleted = (int) lines.stream().filter(line -> "DELETE".equals(line.type())).count();
        return new BlogPostVersionDiffSectionResponse(fieldName, added > 0 || deleted > 0, added, deleted, lines);
    }

    private List<BlogPostVersionDiffLineResponse> buildLineDiff(List<String> oldLines, List<String> newLines) {
        int[][] lcs = new int[oldLines.size() + 1][newLines.size() + 1];
        for (int i = oldLines.size() - 1; i >= 0; i--) {
            for (int j = newLines.size() - 1; j >= 0; j--) {
                if (oldLines.get(i).equals(newLines.get(j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        List<BlogPostVersionDiffLineResponse> result = new ArrayList<>();
        int oldIndex = 0;
        int newIndex = 0;
        while (oldIndex < oldLines.size() && newIndex < newLines.size()) {
            if (oldLines.get(oldIndex).equals(newLines.get(newIndex))) {
                result.add(new BlogPostVersionDiffLineResponse("EQUAL", oldIndex + 1, newIndex + 1, oldLines.get(oldIndex)));
                oldIndex++;
                newIndex++;
            } else if (lcs[oldIndex + 1][newIndex] >= lcs[oldIndex][newIndex + 1]) {
                result.add(new BlogPostVersionDiffLineResponse("DELETE", oldIndex + 1, null, oldLines.get(oldIndex)));
                oldIndex++;
            } else {
                result.add(new BlogPostVersionDiffLineResponse("INSERT", null, newIndex + 1, newLines.get(newIndex)));
                newIndex++;
            }
        }
        while (oldIndex < oldLines.size()) {
            result.add(new BlogPostVersionDiffLineResponse("DELETE", oldIndex + 1, null, oldLines.get(oldIndex)));
            oldIndex++;
        }
        while (newIndex < newLines.size()) {
            result.add(new BlogPostVersionDiffLineResponse("INSERT", null, newIndex + 1, newLines.get(newIndex)));
            newIndex++;
        }
        return result;
    }

    private List<String> splitLines(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(value.split("\\R", -1));
    }
}
