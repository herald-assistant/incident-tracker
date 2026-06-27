package pl.mkn.tdw.api.githubauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pl.mkn.tdw.integrations.github.auth.GitHubAppAuthProperties;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OperatorSessionService {

    private static final int SESSION_BYTES = 32;

    private final GitHubAppAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String getOrCreateSessionId(HttpServletRequest request, HttpServletResponse response) {
        return currentSessionId(request).orElseGet(() -> {
            var sessionId = newSessionId();
            writeCookie(response, sessionId);
            return sessionId;
        });
    }

    public Optional<String> currentSessionId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }

        return currentSessionId(servletAttributes.getRequest());
    }

    public Optional<String> currentSessionId(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return Optional.empty();
        }

        for (Cookie cookie : request.getCookies()) {
            if (properties.getCookieName().equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return Optional.of(cookie.getValue());
            }
        }

        return Optional.empty();
    }

    public String requireCurrentSessionId() {
        return currentSessionId().orElse(null);
    }

    private String newSessionId() {
        var bytes = new byte[SESSION_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void writeCookie(HttpServletResponse response, String sessionId) {
        var cookie = ResponseCookie.from(properties.getCookieName(), sessionId)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(normalizedSameSite())
                .path("/")
                .maxAge(cookieMaxAge())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String normalizedSameSite() {
        return StringUtils.hasText(properties.getCookieSameSite())
                ? properties.getCookieSameSite().trim()
                : "lax";
    }

    private Duration cookieMaxAge() {
        return properties.getCookieMaxAge() != null ? properties.getCookieMaxAge() : Duration.ofDays(30);
    }
}
