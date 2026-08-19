package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

public record GitLabTypeScriptSymbolSelector(
        String name,
        GitLabTypeScriptSymbolKind kind,
        Integer lineStart
) {
    public GitLabTypeScriptSymbolSelector {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("TypeScript symbol selector name must not be blank");
        }
        name = name.trim();
        kind = kind != null ? kind : GitLabTypeScriptSymbolKind.AUTO;
        lineStart = lineStart != null && lineStart > 0 ? lineStart : null;
    }
}
