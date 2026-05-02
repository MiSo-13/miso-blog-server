package com.miso.blog.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Miso Blog Server API")
                        .description("Git 기반 기술 이슈를 분석하고 AI 블로그 글을 생성하는 서버 API")
                        .version("v1"));
    }
}
