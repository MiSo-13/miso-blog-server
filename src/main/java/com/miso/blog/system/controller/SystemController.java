package com.miso.blog.system.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.system.dto.SystemHealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    @GetMapping("/health")
    public ApiDataResponse<SystemHealthResponse> health() {
        return ApiDataResponse.ok(new SystemHealthResponse(
                "UP",
                "miso-blog-server",
                LocalDateTime.now()
        ));
    }
}
