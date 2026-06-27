package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaResolvedType(
        String requestedName,
        GitLabJavaTypeResolutionKind kind,
        GitLabEndpointUseCaseConfidence confidence,
        GitLabJavaTypeDeclaration type,
        String filePath,
        String qualifiedName,
        List<String> candidates,
        List<String> limitations
) {
    public GitLabJavaResolvedType {
        requestedName = GitLabEndpointUseCaseModelSupport.trimToNull(requestedName);
        kind = kind != null ? kind : GitLabJavaTypeResolutionKind.UNRESOLVED;
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        qualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(qualifiedName);
        candidates = GitLabEndpointUseCaseModelSupport.copyStrings(candidates);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
    }

    public boolean resolved() {
        return type != null && filePath != null;
    }
}
