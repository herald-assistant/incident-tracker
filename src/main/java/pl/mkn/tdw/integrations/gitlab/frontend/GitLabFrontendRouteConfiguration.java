package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendRouteConfiguration(
        GitLabFrontendRouteConfigurationKind kind,
        String key,
        List<String> referencedSymbols,
        String staticValue,
        GitLabFrontendDiscoveryStatus status,
        GitLabFrontendSourceReference source,
        List<String> limitations
) {

    public GitLabFrontendRouteConfiguration {
        kind = Objects.requireNonNull(kind, "kind must not be null");
        key = normalize(key);
        referencedSymbols = referencedSymbols != null ? List.copyOf(referencedSymbols) : List.of();
        staticValue = normalize(staticValue);
        status = Objects.requireNonNull(status, "status must not be null");
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
