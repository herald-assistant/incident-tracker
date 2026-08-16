package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

public record GitLabFrontendRouteTarget(
        String symbol,
        String sourcePath
) {

    public GitLabFrontendRouteTarget {
        symbol = normalize(symbol);
        sourcePath = normalize(sourcePath);
        if (symbol == null && sourcePath == null) {
            throw new IllegalArgumentException("route target requires symbol or sourcePath");
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
