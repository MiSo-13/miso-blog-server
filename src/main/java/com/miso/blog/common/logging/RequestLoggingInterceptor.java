package com.miso.blog.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {
    private static final String START_TIME_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".START_TIME";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        // 본문이나 인증 값은 남기지 않고 요청 추적에 필요한 최소 정보만 기록합니다.
        long elapsedMs = System.currentTimeMillis() - getStartTime(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String path = queryString == null || queryString.isBlank() ? uri : uri + "?" + queryString;
        String clientIp = resolveClientIp(request);
        String userAgent = trimToDash(request.getHeader("User-Agent"));

        if (exception == null) {
            log.info(
                    "request method={} path={} status={} elapsedMs={} clientIp={} userAgent={}",
                    method,
                    path,
                    response.getStatus(),
                    elapsedMs,
                    clientIp,
                    userAgent
            );
            return;
        }

        log.warn(
                "request method={} path={} status={} elapsedMs={} clientIp={} userAgent={} exception={}",
                method,
                path,
                response.getStatus(),
                elapsedMs,
                clientIp,
                userAgent,
                exception.getClass().getSimpleName()
        );
    }

    private long getStartTime(HttpServletRequest request) {
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime instanceof Long value) {
            return value;
        }
        return System.currentTimeMillis();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = trimToNull(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return trimToDash(request.getRemoteAddr());
    }

    private String trimToDash(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "-" : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
