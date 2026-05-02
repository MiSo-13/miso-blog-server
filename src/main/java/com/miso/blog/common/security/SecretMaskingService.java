package com.miso.blog.common.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SecretMaskingService {
    private static final String MASK = "[MASKED]";
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN ([A-Z ]*PRIVATE KEY)-----[\\s\\S]*?-----END \\1-----"
    );
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?im)(Authorization\\s*:\\s*(?:Bearer|Basic)\\s+)[^\\s]+"
    );
    private static final Pattern JSON_SECRET_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"[\\w.-]*(?:api[-_]?key|token|password|passwd|pwd|secret|client[-_]?secret|access[-_]?token|refresh[-_]?token)[\\w.-]*\"\\s*:\\s*\")([^\"\\r\\n]*)(\")"
    );
    private static final Pattern KEY_VALUE_SECRET_PATTERN = Pattern.compile(
            "(?im)^([\\s\\-]*[\\w.-]*(?:api[-_]?key|token|password|passwd|pwd|secret|client[-_]?secret|access[-_]?token|refresh[-_]?token)[\\w.-]*\\s*[:=]\\s*)([^\\s#]+)"
    );
    private static final Pattern JDBC_CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(jdbc:[^\\s]+://[^\\s:/@]+:)([^\\s@]+)(@)"
    );
    private static final List<Pattern> TOKEN_PATTERNS = List.of(
            Pattern.compile("\\bsk-(?:proj-|admin-)?[A-Za-z0-9_-]{16,}\\b"),
            Pattern.compile("\\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{16,}\\b"),
            Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{20,}\\b"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b")
    );

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String masked = replacePrivateKeys(value);
        masked = replaceValueGroup(masked, AUTHORIZATION_PATTERN);
        masked = replaceMiddleGroup(masked, JSON_SECRET_FIELD_PATTERN);
        masked = replaceValueGroup(masked, KEY_VALUE_SECRET_PATTERN);
        masked = replaceMiddleGroup(masked, JDBC_CREDENTIAL_PATTERN);

        // 대표적인 API key/token 형태는 라인 맥락이 없어도 한 번 더 가린다.
        for (Pattern pattern : TOKEN_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(MASK);
        }
        return masked;
    }

    private String replacePrivateKeys(String value) {
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = "-----BEGIN " + matcher.group(1)
                    + "-----\n[MASKED_PRIVATE_KEY]\n-----END " + matcher.group(1) + "-----";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceValueGroup(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + MASK));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceMiddleGroup(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + MASK + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
