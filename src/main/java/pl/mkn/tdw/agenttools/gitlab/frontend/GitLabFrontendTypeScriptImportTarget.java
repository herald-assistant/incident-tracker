package pl.mkn.tdw.agenttools.gitlab.frontend;

import org.springframework.util.StringUtils;

public record GitLabFrontendTypeScriptImportTarget(
        String consumerFilePath,
        String moduleSpecifier,
        String importedSymbol,
        GitLabFrontendTypeScriptSliceTarget target
) {
    public GitLabFrontendTypeScriptImportTarget {
        if (!StringUtils.hasText(consumerFilePath)) {
            throw new IllegalArgumentException("consumerFilePath must not be blank");
        }
        if (!StringUtils.hasText(moduleSpecifier)) {
            throw new IllegalArgumentException("moduleSpecifier must not be blank");
        }
        if (!StringUtils.hasText(importedSymbol)) {
            throw new IllegalArgumentException("importedSymbol must not be blank");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        consumerFilePath = normalizePath(consumerFilePath);
        moduleSpecifier = moduleSpecifier.trim();
        importedSymbol = importedSymbol.trim();
    }

    public String importKey() {
        return key(consumerFilePath, moduleSpecifier, importedSymbol);
    }

    public static String key(String consumerFilePath, String moduleSpecifier, String importedSymbol) {
        return normalizePath(consumerFilePath) + "\n" + normalize(moduleSpecifier) + "\n" + normalize(importedSymbol);
    }

    private static String normalizePath(String value) {
        return StringUtils.hasText(value) ? value.trim().replace('\\', '/') : "";
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
