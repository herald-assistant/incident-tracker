package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaMapStructMapper(
        String simpleName,
        String relativeName,
        String qualifiedName,
        String filePath,
        int lineStart,
        int lineEnd,
        List<String> usesTypes,
        List<String> declaredMethodNames,
        List<String> defaultMethodNames,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaMapStructMapper {
        simpleName = GitLabEndpointUseCaseModelSupport.trimToNull(simpleName);
        relativeName = GitLabEndpointUseCaseModelSupport.trimToNull(relativeName);
        qualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(qualifiedName);
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
        usesTypes = GitLabEndpointUseCaseModelSupport.copyStrings(usesTypes);
        declaredMethodNames = GitLabEndpointUseCaseModelSupport.copyStrings(declaredMethodNames);
        defaultMethodNames = GitLabEndpointUseCaseModelSupport.copyStrings(defaultMethodNames);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
