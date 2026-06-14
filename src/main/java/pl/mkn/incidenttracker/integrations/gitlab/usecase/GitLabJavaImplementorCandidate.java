package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaImplementorCandidate(
        String interfaceName,
        String implementationSimpleName,
        String implementationRelativeName,
        String implementationQualifiedName,
        GitLabJavaTypeKind implementationKind,
        String filePath,
        int lineStart,
        int lineEnd,
        List<String> implementedTypes,
        GitLabEndpointUseCaseConfidence confidence,
        String reason
) {
    public GitLabJavaImplementorCandidate {
        interfaceName = GitLabEndpointUseCaseModelSupport.trimToNull(interfaceName);
        implementationSimpleName = GitLabEndpointUseCaseModelSupport.trimToNull(implementationSimpleName);
        implementationRelativeName = GitLabEndpointUseCaseModelSupport.trimToNull(implementationRelativeName);
        implementationQualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(implementationQualifiedName);
        implementationKind = implementationKind != null ? implementationKind : GitLabJavaTypeKind.UNKNOWN;
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
        implementedTypes = GitLabEndpointUseCaseModelSupport.copyStrings(implementedTypes);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
    }
}
