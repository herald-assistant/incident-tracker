package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.List;

public record GitLabFrontendRouteTarget(
        String symbol,
        String sourcePath,
        List<String> selectors
) {

    public GitLabFrontendRouteTarget {
        symbol = normalize(symbol);
        sourcePath = normalize(sourcePath);
        selectors = selectors != null ? List.copyOf(selectors) : List.of();
        if (symbol == null && sourcePath == null) {
            throw new IllegalArgumentException("route target requires symbol or sourcePath");
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
