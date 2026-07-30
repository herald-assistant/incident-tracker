package pl.mkn.tdw.integrations.gitlab;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GitLabNamedConnectionRegistry {

    private static final Pattern CONNECTION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private final GitLabNamedConnectionsProperties properties;

    public GitLabConnectionDetails require(String connectionId) {
        var normalizedId = normalizeId(connectionId);
        var configured = properties.getConnections().get(normalizedId);
        if (configured == null) {
            throw GitLabExactReadException.connectionNotFound(normalizedId);
        }

        var baseUrl = normalizeBaseUrl(configured.getBaseUrl(), normalizedId);
        return new GitLabConnectionDetails(
                normalizedId,
                baseUrl,
                configured.getToken(),
                configured.isIgnoreSslErrors()
        );
    }

    public boolean contains(String connectionId) {
        try {
            require(connectionId);
            return true;
        } catch (GitLabExactReadException ignored) {
            return false;
        }
    }

    public List<String> connectionIds() {
        return properties.getConnections().keySet().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(id -> CONNECTION_ID.matcher(id).matches())
                .sorted()
                .toList();
    }

    public int maxFileCharacters() {
        return Math.max(1, properties.getMaxFileCharacters());
    }

    private String normalizeId(String connectionId) {
        var normalized = StringUtils.hasText(connectionId) ? connectionId.trim() : "";
        if (!CONNECTION_ID.matcher(normalized).matches()) {
            throw GitLabExactReadException.invalidTarget("GitLab connection id is invalid.");
        }
        return normalized;
    }

    private String normalizeBaseUrl(String baseUrl, String connectionId) {
        if (!StringUtils.hasText(baseUrl)) {
            throw GitLabExactReadException.connectionInvalid(connectionId);
        }

        try {
            var uri = URI.create(baseUrl.trim());
            var scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            if (!List.of("http", "https").contains(scheme)
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw GitLabExactReadException.connectionInvalid(connectionId);
            }

            var normalized = baseUrl.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw GitLabExactReadException.connectionInvalid(connectionId);
        }
    }
}
