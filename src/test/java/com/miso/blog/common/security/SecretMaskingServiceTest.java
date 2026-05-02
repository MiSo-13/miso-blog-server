package com.miso.blog.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretMaskingServiceTest {
    private final SecretMaskingService secretMaskingService = new SecretMaskingService();

    @Test
    void maskHidesCommonApiKeysAndPasswords() {
        String openAiKey = "sk-" + "proj-" + "abcdefghijklmnopqrstuvwxyz123456";
        String githubToken = "ghp_" + "abcdefghijklmnopqrstuvwxyz123456";
        String source = """
                blog.ai.api-key: %s
                github.token=%s
                spring.datasource.password: local-secret
                {"access_token":"json-token-value"}
                Authorization: Bearer bearer-token-value
                """.formatted(openAiKey, githubToken);

        String masked = secretMaskingService.mask(source);

        assertTrue(masked.contains("[MASKED]"));
        assertFalse(masked.contains(openAiKey));
        assertFalse(masked.contains(githubToken));
        assertFalse(masked.contains("local-secret"));
        assertFalse(masked.contains("json-token-value"));
        assertFalse(masked.contains("bearer-token-value"));
    }

    @Test
    void maskHidesPrivateKeyBlocksAndJdbcPasswords() {
        String source = """
                jdbc:mysql://blog-user:blog-password@localhost:3306/miso-blog
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEAsecretprivatekeybody
                -----END RSA PRIVATE KEY-----
                """;

        String masked = secretMaskingService.mask(source);

        assertTrue(masked.contains("[MASKED]"));
        assertTrue(masked.contains("[MASKED_PRIVATE_KEY]"));
        assertFalse(masked.contains("blog-password"));
        assertFalse(masked.contains("secretprivatekeybody"));
    }
}
