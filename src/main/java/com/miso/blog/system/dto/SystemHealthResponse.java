package com.miso.blog.system.dto;

import java.time.LocalDateTime;

public record SystemHealthResponse(
        String status,
        String serviceName,
        LocalDateTime checkedAt
) {
}
