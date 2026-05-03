package com.miso.blog.media.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.media.dto.BlogMediaAssetResponse;
import com.miso.blog.media.entity.BlogMediaAssetEntity;
import com.miso.blog.media.repository.BlogMediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BlogMediaAssetService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final BlogMediaAssetRepository blogMediaAssetRepository;
    private final BlogMediaPathResolver blogMediaPathResolver;

    @Value("${blog.media.upload-dir:uploads/blog-media}")
    private String uploadDir;

    @Value("${blog.media.public-url-prefix:/media}")
    private String publicUrlPrefix;

    @Value("${blog.media.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes;

    @Transactional
    public BlogMediaAssetResponse uploadImage(MultipartFile file, String altText, String note) {
        validateFile(file);

        String originalFilename = blogMediaPathResolver.sanitizeFilename(file.getOriginalFilename());
        String dateDirectory = blogMediaPathResolver.dateDirectory(LocalDate.now());
        String storedFilename = blogMediaPathResolver.storedFilename(originalFilename);
        String relativePath = dateDirectory + "/" + storedFilename;
        Path targetPath = Path.of(uploadDir).toAbsolutePath().normalize()
                .resolve(dateDirectory)
                .resolve(storedFilename)
                .normalize();

        ensureInsideUploadDir(targetPath);
        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "이미지 파일을 저장할 수 없습니다.");
        }

        BlogMediaAssetEntity asset = blogMediaAssetRepository.save(BlogMediaAssetEntity.builder()
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .relativePath(relativePath)
                .publicUrl(buildPublicUrl(relativePath))
                .altText(trimToNull(altText))
                .note(trimToNull(note))
                .build());
        return BlogMediaAssetResponse.from(asset);
    }

    @Transactional(readOnly = true)
    public List<BlogMediaAssetResponse> getAssets() {
        return blogMediaAssetRepository.findAllByOrderByIdDesc()
                .stream()
                .map(BlogMediaAssetResponse::from)
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "업로드할 이미지 파일이 필요합니다.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "이미지 파일 크기가 허용 범위를 초과했습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "jpg, png, webp, gif 이미지만 업로드할 수 있습니다.");
        }
    }

    private void ensureInsideUploadDir(Path targetPath) {
        Path rootPath = Path.of(uploadDir).toAbsolutePath().normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "이미지 저장 경로가 올바르지 않습니다.");
        }
    }

    private String buildPublicUrl(String relativePath) {
        return blogMediaPathResolver.normalizePublicPrefix(publicUrlPrefix) + "/" + relativePath.replace('\\', '/');
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
