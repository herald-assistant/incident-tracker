package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record GitLabTypeScriptSymbolSliceRequest(
        GitLabFrontendRepositoryScope scope,
        String filePath,
        String declaringTypeName,
        String templatePath,
        Boolean includeTemplateBindings,
        List<GitLabTypeScriptSymbolSelector> symbolSelectors,
        Boolean includeLocalHelpers,
        Boolean includeRelevantFields,
        Boolean includeRelevantImports,
        Integer maxCharacters
) {
    public GitLabTypeScriptSymbolSliceRequest {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        filePath = GitLabFrontendTargetedSourceSession.normalize(filePath);
        if ((!filePath.endsWith(".ts") && !filePath.endsWith(".tsx"))
                || filePath.contains("..") || filePath.startsWith("/")) {
            throw new IllegalArgumentException("filePath must point to a repository TypeScript source");
        }
        declaringTypeName = StringUtils.hasText(declaringTypeName) ? declaringTypeName.trim() : null;
        templatePath = StringUtils.hasText(templatePath)
                ? GitLabFrontendTargetedSourceSession.normalize(templatePath)
                : null;
        if (templatePath != null && ((!templatePath.endsWith(".html") && !templatePath.endsWith(".htm"))
                || templatePath.contains("..") || templatePath.startsWith("/"))) {
            throw new IllegalArgumentException("templatePath must point to a repository HTML source");
        }
        symbolSelectors = symbolSelectors != null ? List.copyOf(symbolSelectors) : List.of();
    }
}
