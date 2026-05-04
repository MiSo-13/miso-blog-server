package com.miso.blog.reference.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlogReferenceContentFetcherTest {
    private final BlogReferenceContentFetcher fetcher = new BlogReferenceContentFetcher();

    @Test
    void extractPageReadsTitleDescriptionAndMeaningfulBlocks() {
        BlogReferenceContentFetcher.ExtractedReferencePage page = fetcher.extractPage("""
                <html>
                  <head>
                    <title>성수동 카페 후기</title>
                    <meta name="description" content="따뜻한 조명과 조용한 분위기를 중심으로 정리한 후기">
                  </head>
                  <body>
                    <script>console.log('ignore');</script>
                    <h1>성수동에서 조용하게 머물기 좋은 카페</h1>
                    <p>처음 들어갔을 때는 테이블 사이 간격이 넉넉해서 대화하기 편한 분위기라는 점이 먼저 눈에 들어왔습니다.</p>
                    <p>커피 맛을 과하게 포장하기보다, 오래 앉아도 부담 없는 공간감과 조명에 초점을 맞춰 소개했습니다.</p>
                  </body>
                </html>
                """);

        assertEquals("성수동 카페 후기", page.title());
        assertEquals("따뜻한 조명과 조용한 분위기를 중심으로 정리한 후기", page.metaDescription());
        assertTrue(page.excerpts().stream().anyMatch(excerpt -> excerpt.contains("테이블 사이 간격")));
        assertTrue(page.excerpts().stream().noneMatch(excerpt -> excerpt.contains("console.log")));
    }
}
