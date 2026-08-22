package pl.mkn.tdw.agenttools.gitlab.frontend;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;

import java.util.List;

public record GitLabFrontendTypeScriptSliceTarget(
        String sliceRef,
        String filePath,
        String declaringTypeName,
        String templatePath,
        List<GitLabTypeScriptSymbolSelector> symbolSelectors
) {
    public GitLabFrontendTypeScriptSliceTarget {
        if (!StringUtils.hasText(sliceRef)) {
            throw new IllegalArgumentException("sliceRef must not be blank");
        }
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        sliceRef = sliceRef.trim();
        filePath = filePath.trim();
        declaringTypeName = StringUtils.hasText(declaringTypeName) ? declaringTypeName.trim() : null;
        templatePath = StringUtils.hasText(templatePath) ? templatePath.trim() : null;
        symbolSelectors = symbolSelectors != null ? List.copyOf(symbolSelectors) : List.of();
    }
}
