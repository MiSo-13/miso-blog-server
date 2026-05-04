package com.miso.blog.publish.service;

import com.miso.blog.publish.dto.GitHubContentFile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class JekyllSiteScaffoldFormatter {
    public List<GitHubContentFile> buildFiles(
            String siteTitle,
            String siteDescription,
            String authorName,
            String publicBaseUrl
    ) {
        JekyllBaseUrl jekyllBaseUrl = splitBaseUrl(publicBaseUrl);
        String title = defaultText(siteTitle, "Miso Tech Blog");
        String description = defaultText(siteDescription, "개발하며 배운 점을 기록하는 기술 블로그입니다.");
        String author = defaultText(authorName, "MiSo");

        List<GitHubContentFile> files = new ArrayList<>();
        files.add(new GitHubContentFile("_config.yml", configYaml(title, description, author, jekyllBaseUrl)));
        files.add(new GitHubContentFile("index.md", indexMarkdown()));
        files.add(new GitHubContentFile("about.md", aboutMarkdown(title, description, author)));
        files.add(new GitHubContentFile("_layouts/default.html", defaultLayout()));
        files.add(new GitHubContentFile("_layouts/post.html", postLayout()));
        files.add(new GitHubContentFile("assets/css/style.css", styleCss()));
        return files;
    }

    public List<String> requiredFilePaths() {
        return List.of(
                "_config.yml",
                "index.md",
                "about.md",
                "_layouts/default.html",
                "_layouts/post.html",
                "assets/css/style.css"
        );
    }

    private String configYaml(String siteTitle, String siteDescription, String authorName, JekyllBaseUrl baseUrl) {
        return """
                title: %s
                description: %s
                author: %s
                url: %s
                baseurl: %s
                permalink: /:year/:month/:day/:title.html
                markdown: kramdown
                timezone: Asia/Seoul
                exclude:
                  - README.md
                """.formatted(
                quoteYaml(siteTitle),
                quoteYaml(siteDescription),
                quoteYaml(authorName),
                quoteYaml(baseUrl.url()),
                quoteYaml(baseUrl.basePath())
        );
    }

    private String indexMarkdown() {
        return """
                ---
                layout: default
                title: Home
                ---

                # Tech Blog

                {% if site.posts.size > 0 %}
                <section class="post-list">
                  {% for post in site.posts %}
                  <article class="post-card">
                    <p class="post-date">{{ post.date | date: "%Y.%m.%d" }}</p>
                    <h2><a href="{{ post.url | relative_url }}">{{ post.title }}</a></h2>
                    {% if post.description %}
                    <p>{{ post.description }}</p>
                    {% endif %}
                  </article>
                  {% endfor %}
                </section>
                {% else %}
                아직 발행된 글이 없습니다.
                {% endif %}
                """;
    }

    private String aboutMarkdown(String siteTitle, String siteDescription, String authorName) {
        return """
                ---
                layout: default
                title: About
                permalink: /about/
                ---

                # About

                **%s**는 %s

                작성자: %s
                """.formatted(siteTitle, siteDescription, authorName);
    }

    private String defaultLayout() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>{% if page.title %}{{ page.title }} | {% endif %}{{ site.title }}</title>
                  {% if page.description %}
                  <meta name="description" content="{{ page.description }}">
                  {% else %}
                  <meta name="description" content="{{ site.description }}">
                  {% endif %}
                  <link rel="stylesheet" href="{{ '/assets/css/style.css' | relative_url }}">
                </head>
                <body>
                  <header class="site-header">
                    <a class="site-title" href="{{ '/' | relative_url }}">{{ site.title }}</a>
                    <nav>
                      <a href="{{ '/' | relative_url }}">Posts</a>
                      <a href="{{ '/about/' | relative_url }}">About</a>
                    </nav>
                  </header>
                  <main class="site-main">
                    {{ content }}
                  </main>
                </body>
                </html>
                """;
    }

    private String postLayout() {
        return """
                ---
                layout: default
                ---

                <article class="post">
                  <header class="post-header">
                    <p class="post-date">{{ page.date | date: "%Y.%m.%d" }}</p>
                    <h1>{{ page.title }}</h1>
                    {% if page.description %}
                    <p class="post-description">{{ page.description }}</p>
                    {% endif %}
                  </header>
                  <div class="post-content">
                    {{ content }}
                  </div>
                </article>
                """;
    }

    private String styleCss() {
        return """
                :root {
                  color-scheme: light;
                  --bg: #f7f3eb;
                  --surface: #fffaf1;
                  --text: #20201d;
                  --muted: #756f64;
                  --accent: #b5532f;
                  --line: #e5dac8;
                }

                body {
                  margin: 0;
                  background: radial-gradient(circle at top left, #fff5d7, transparent 32rem), var(--bg);
                  color: var(--text);
                  font-family: "Pretendard", "Apple SD Gothic Neo", "Malgun Gothic", sans-serif;
                  line-height: 1.72;
                }

                a { color: var(--accent); text-decoration-thickness: 0.08em; }

                .site-header {
                  display: flex;
                  justify-content: space-between;
                  gap: 1rem;
                  max-width: 920px;
                  margin: 0 auto;
                  padding: 2rem 1.25rem 1rem;
                }

                .site-title { color: var(--text); font-weight: 800; text-decoration: none; }
                nav { display: flex; gap: 1rem; }

                .site-main {
                  max-width: 920px;
                  margin: 0 auto;
                  padding: 2rem 1.25rem 5rem;
                }

                .post-card, .post {
                  background: color-mix(in srgb, var(--surface) 92%, white);
                  border: 1px solid var(--line);
                  border-radius: 24px;
                  box-shadow: 0 24px 70px rgba(70, 48, 24, 0.08);
                }

                .post-card { padding: 1.5rem; margin: 1rem 0; }
                .post { padding: clamp(1.5rem, 4vw, 3rem); }
                .post-date, .post-description { color: var(--muted); }
                .post-content img { max-width: 100%; border-radius: 18px; }
                .post-content pre { overflow-x: auto; padding: 1rem; border-radius: 16px; background: #1f211f; color: #f7f3eb; }
                .post-content code { font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace; }
                """;
    }

    private JekyllBaseUrl splitBaseUrl(String publicBaseUrl) {
        String fallbackUrl = "https://miso-13.github.io";
        String normalized = defaultText(publicBaseUrl, fallbackUrl).replaceAll("/+$", "");
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return new JekyllBaseUrl(normalized, "");
            }
            String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return new JekyllBaseUrl(scheme + "://" + host + port, path);
        } catch (IllegalArgumentException exception) {
            return new JekyllBaseUrl(normalized, "");
        }
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String quoteYaml(String value) {
        String safeValue = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", " ");
        return "\"" + safeValue + "\"";
    }

    private record JekyllBaseUrl(String url, String basePath) {
    }
}
