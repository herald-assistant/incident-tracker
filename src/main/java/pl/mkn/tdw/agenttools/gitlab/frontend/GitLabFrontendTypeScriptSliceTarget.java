package pl.mkn.tdw.agenttools.gitlab.frontend;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;

import java.util.List;

public record GitLabFrontendTypeScriptSliceTarget(
        String filePath,
        String declaringTypeName,
        String templatePath,
        List<GitLabTypeScriptSymbolSelector> symbolSelectors
) {
    public GitLabFrontendTypeScriptSliceTarget {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        filePath = normalizePath(filePath);
        declaringTypeName = StringUtils.hasText(declaringTypeName) ? declaringTypeName.trim() : null;
        templatePath = StringUtils.hasText(templatePath) ? templatePath.trim() : null;
        symbolSelectors = symbolSelectors != null ? List.copyOf(symbolSelectors) : List.of();
    }

    public String targetKey() {
        return key(filePath, declaringTypeName);
    }

    public static String key(String filePath, String declaringTypeName) {
        return normalizePath(filePath) + "\n" + normalizeText(declaringTypeName);
    }

    private static String normalizePath(String value) {
        return StringUtils.hasText(value) ? value.trim().replace('\\', '/') : "";
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
