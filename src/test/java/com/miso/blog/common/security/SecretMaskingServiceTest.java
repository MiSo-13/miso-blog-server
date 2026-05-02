package com.miso.blog.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretMaskingServiceTest {
    private final SecretMaskingService secretMaskingService = new SecretMaskingService();

    @Test
    void maskHidesCommonApiKeysAndPasswords() {
        String source = """
                blog.ai.api-key: openai-test-key-value
                github.token=github-test-token-value
                spring.datasource.password: local-secret
                {"access_token":"json-token-value"}
                Authorization: Bearer bearer-token-value
                """;

        String masked = secretMaskingService.mask(source);

        assertTrue(masked.contains("[MASKED]"));
        assertFalse(masked.contains("openai-test-key-value"));
        assertFalse(masked.contains("github-test-token-value"));
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
