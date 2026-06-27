package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaInjectedDependency(
        String name,
        String typeName,
        GitLabJavaInjectionSource source,
        String qualifier,
        String declaringTypeQualifiedName,
        String filePath,
        int lineStart,
        int lineEnd,
        List<String> annotations,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaInjectedDependency {
        name = GitLabEndpointUseCaseModelSupport.trimToNull(name);
        typeName = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        source = source != null ? source : GitLabJavaInjectionSource.CONSTRUCTOR;
        qualifier = GitLabEndpointUseCaseModelSupport.trimToNull(qualifier);
        declaringTypeQualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeQualifiedName);
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
        annotations = GitLabEndpointUseCaseModelSupport.copyStrings(annotations);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
