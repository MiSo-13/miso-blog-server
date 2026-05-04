package com.miso.blog.reference.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BlogReferenceContentFetcher {
    private static final int MAX_RESPONSE_BYTES = 500_000;
    private static final int MAX_EXCERPT_COUNT = 8;
    private static final int MAX_EXCERPT_LENGTH = 450;
    private static final int MIN_EXCERPT_LENGTH = 25;
    private static final Pattern REMOVED_TAG_PATTERN = Pattern.compile(
            "(?is)<(script|style|noscript|svg|iframe|template)[^>]*>.*?</\\1>"
    );
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern META_TAG_PATTERN = Pattern.compile("(?is)<meta\\s+[^>]*>");
    private static final Pattern HTML_BLOCK_PATTERN = Pattern.compile("(?is)<(h1|h2|h3|p|li|blockquote)[^>]*>(.*?)</\\1>");
    private static final Pattern ATTRIBUTE_PATTERN_TEMPLATE = Pattern.compile("%s\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(x?[0-9a-fA-F]+);");
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset\\s*=\\s*([^;\\s]+)");
    private static final Set<String> READABLE_CONTENT_TYPES = Set.of(
            "text/html",
            "text/plain",
            "application/xhtml+xml"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public FetchedReferenceContent fetch(String url) {
        URI uri = toHttpUri(url);
        if (uri == null) {
            return FetchedReferenceContent.failed(url, "http 또는 https URL만 실제 본문 확인을 지원합니다.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.1")
                    .header("User-Agent", "MisoBlogReferenceFetcher/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                closeQuietly(response.body());
                return FetchedReferenceContent.failed(url, response.uri().toString(), statusCode, "본문 요청 실패 status=" + statusCode);
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!isReadableContentType(contentType)) {
                closeQuietly(response.body());
                return FetchedReferenceContent.failed(url, response.uri().toString(), statusCode, "HTML/Text가 아닌 응답입니다. contentType=" + contentType);
            }

            byte[] bytes = readLimitedBytes(response.body());
            String html = new String(bytes, resolveCharset(contentType));
            ExtractedReferencePage page = extractPage(html);
            if (!page.hasContent()) {
                return FetchedReferenceContent.failed(url, response.uri().toString(), statusCode, "본문에서 읽을 만한 문장을 찾지 못했습니다.");
            }
            return new FetchedReferenceContent(
                    url,
                    response.uri().toString(),
                    statusCode,
                    true,
                    page.title(),
                    page.metaDescription(),
                    page.excerpts(),
                    null
            );
        } catch (IOException exception) {
            return FetchedReferenceContent.failed(url, "네트워크 오류로 본문을 확인하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FetchedReferenceContent.failed(url, "본문 확인이 중단되었습니다.");
        } catch (Exception exception) {
            return FetchedReferenceContent.failed(url, "본문 확인 중 오류가 발생했습니다.");
        }
    }

    ExtractedReferencePage extractPage(String html) {
        if (html == null || html.isBlank()) {
            return new ExtractedReferencePage(null, null, List.of());
        }

        String cleanedHtml = REMOVED_TAG_PATTERN.matcher(html).replaceAll(" ");
        String title = cleanText(firstMatch(TITLE_PATTERN, cleanedHtml));
        String metaDescription = cleanText(findMetaDescription(cleanedHtml));
        List<String> excerpts = extractBlockExcerpts(cleanedHtml);
        if (excerpts.isEmpty()) {
            excerpts = extractSentenceExcerpts(cleanText(stripTags(cleanedHtml)));
        }
        return new ExtractedReferencePage(
                blankToNull(limit(title, MAX_EXCERPT_LENGTH)),
                blankToNull(limit(metaDescription, MAX_EXCERPT_LENGTH)),
                excerpts
        );
    }

    private List<String> extractBlockExcerpts(String html) {
        Matcher matcher = HTML_BLOCK_PATTERN.matcher(html);
        LinkedHashSet<String> excerpts = new LinkedHashSet<>();
        while (matcher.find() && excerpts.size() < MAX_EXCERPT_COUNT) {
            String text = cleanText(stripTags(matcher.group(2)));
            addExcerpt(excerpts, text);
        }
        return new ArrayList<>(excerpts);
    }

    private List<String> extractSentenceExcerpts(String text) {
        LinkedHashSet<String> excerpts = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        for (String sentence : text.split("(?<=[.!?。！？])\\s+|\\R+")) {
            if (excerpts.size() >= MAX_EXCERPT_COUNT) {
                break;
            }
            addExcerpt(excerpts, sentence);
        }
        return new ArrayList<>(excerpts);
    }

    private void addExcerpt(LinkedHashSet<String> excerpts, String value) {
        String text = blankToNull(value);
        if (text == null || text.length() < MIN_EXCERPT_LENGTH || isNavigationNoise(text)) {
            return;
        }
        excerpts.add(limit(text, MAX_EXCERPT_LENGTH));
    }

    private boolean isNavigationNoise(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("home")
                || lower.equals("menu")
                || lower.equals("login")
                || lower.contains("javascript")
                || lower.contains("cookie")
                || lower.contains("privacy policy")
                || lower.contains("로그인")
                || lower.contains("회원가입")
                || lower.contains("댓글")
                || lower.contains("공유하기");
    }

    private String findMetaDescription(String html) {
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            String name = attr(tag, "name");
            String property = attr(tag, "property");
            String key = name == null ? property : name;
            if (key == null) {
                continue;
            }
            String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
            if (normalizedKey.equals("description")
                    || normalizedKey.equals("og:description")
                    || normalizedKey.equals("twitter:description")) {
                return attr(tag, "content");
            }
        }
        return null;
    }

    private String attr(String tag, String attrName) {
        Pattern pattern = Pattern.compile(
                ATTRIBUTE_PATTERN_TEMPLATE.pattern().formatted(Pattern.quote(attrName)),
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(tag);
        return matcher.find() ? matcher.group(2) : null;
    }

    private String stripTags(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</(p|div|li|h[1-6]|blockquote)>", "\n")
                .replaceAll("(?is)<[^>]+>", " ");
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        return decodeHtmlEntities(value)
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .replaceAll("\\s+\\n", "\n")
                .trim();
    }

    private String decodeHtmlEntities(String value) {
        String replaced = value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        Matcher matcher = NUMERIC_ENTITY_PATTERN.matcher(replaced);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String entity = matcher.group(1);
            String replacement = decodeNumericEntity(entity);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String decodeNumericEntity(String entity) {
        try {
            int radix = entity.startsWith("x") || entity.startsWith("X") ? 16 : 10;
            String number = radix == 16 ? entity.substring(1) : entity;
            return new String(Character.toChars(Integer.parseInt(number, radix)));
        } catch (IllegalArgumentException exception) {
            return " ";
        }
    }

    private URI toHttpUri(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            return uri;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private boolean isReadableContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return READABLE_CONTENT_TYPES.stream().anyMatch(normalized::contains);
    }

    private Charset resolveCharset(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return StandardCharsets.UTF_8;
        }
        String charsetName = matcher.group(1).replace("\"", "").replace("'", "").trim();
        try {
            return Charset.forName(charsetName);
        } catch (Exception exception) {
            return StandardCharsets.UTF_8;
        }
    }

    private byte[] readLimitedBytes(InputStream inputStream) throws IOException {
        try (InputStream input = inputStream) {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length <= MAX_RESPONSE_BYTES) {
                return bytes;
            }
            return Arrays.copyOf(bytes, MAX_RESPONSE_BYTES);
        }
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private String firstMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).strip() + " ...";
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record FetchedReferenceContent(
            String requestedUrl,
            String finalUrl,
            int statusCode,
            boolean fetched,
            String pageTitle,
            String metaDescription,
            List<String> excerpts,
            String errorMessage
    ) {
        static FetchedReferenceContent failed(String requestedUrl, String errorMessage) {
            return failed(requestedUrl, requestedUrl, 0, errorMessage);
        }

        static FetchedReferenceContent failed(String requestedUrl, String finalUrl, int statusCode, String errorMessage) {
            return new FetchedReferenceContent(
                    requestedUrl,
                    finalUrl,
                    statusCode,
                    false,
                    null,
                    null,
                    List.of(),
                    errorMessage
            );
        }
    }

    record ExtractedReferencePage(
            String title,
            String metaDescription,
            List<String> excerpts
    ) {
        boolean hasContent() {
            return title != null || metaDescription != null || !excerpts.isEmpty();
        }
    }
}
