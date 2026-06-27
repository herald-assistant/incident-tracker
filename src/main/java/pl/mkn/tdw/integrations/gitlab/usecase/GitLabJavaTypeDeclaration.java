package pl.mkn.tdw.integrations.gitlab.usecase;

public record GitLabJavaTypeDeclaration(
        String simpleName,
        String relativeName,
        String qualifiedName,
        GitLabJavaTypeKind kind,
        String filePath,
        boolean topLevel,
        String parentTypeName,
        int lineStart,
        int lineEnd
) {
    public GitLabJavaTypeDeclaration {
        simpleName = GitLabEndpointUseCaseModelSupport.trimToNull(simpleName);
        relativeName = GitLabEndpointUseCaseModelSupport.trimToNull(relativeName);
        qualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(qualifiedName);
        kind = kind != null ? kind : GitLabJavaTypeKind.UNKNOWN;
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        parentTypeName = GitLabEndpointUseCaseModelSupport.trimToNull(parentTypeName);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
    }
}
