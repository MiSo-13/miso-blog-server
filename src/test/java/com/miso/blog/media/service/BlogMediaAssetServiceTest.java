package com.miso.blog.media.service;

import com.miso.blog.media.dto.BlogMediaBatchUploadResponse;
import com.miso.blog.media.entity.BlogMediaAssetEntity;
import com.miso.blog.media.repository.BlogMediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlogMediaAssetServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void uploadImagesStoresFilesWithSameGroupId() throws Exception {
        BlogMediaAssetRepository repository = mock(BlogMediaAssetRepository.class);
        when(repository.save(any(BlogMediaAssetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BlogMediaAssetService service = new BlogMediaAssetService(repository, new BlogMediaPathResolver());
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "publicUrlPrefix", "/media");
        ReflectionTestUtils.setField(service, "maxFileSizeBytes", 1024L * 1024L);

        MockMultipartFile first = new MockMultipartFile("files", "outside.jpg", "image/jpeg", "outside".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "pasta.png", "image/png", "pasta".getBytes());

        BlogMediaBatchUploadResponse response = service.uploadImages(
                List.of(first, second),
                List.of("가게 외관", "파스타 사진"),
                List.of("도입부 근처", "메뉴 설명 문단")
        );

        assertNotNull(response.uploadGroupId());
        assertEquals(2, response.uploadedCount());
        assertEquals(response.uploadGroupId(), response.assets().get(0).uploadGroupId());
        assertEquals(response.uploadGroupId(), response.assets().get(1).uploadGroupId());
        assertEquals("가게 외관", response.assets().get(0).altText());
        assertTrue(response.assets().get(0).publicUrl().startsWith("/media/"));
        try (Stream<Path> paths = Files.walk(tempDir)) {
            assertFalse(paths.filter(Files::isRegularFile).toList().isEmpty());
        }
    }
}
