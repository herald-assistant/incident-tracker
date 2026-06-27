package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaSpringDataRepository(
        String simpleName,
        String relativeName,
        String qualifiedName,
        String filePath,
        int lineStart,
        int lineEnd,
        String baseInterface,
        String entityType,
        String idType,
        List<String> declaredMethodNames,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaSpringDataRepository {
        simpleName = GitLabEndpointUseCaseModelSupport.trimToNull(simpleName);
        relativeName = GitLabEndpointUseCaseModelSupport.trimToNull(relativeName);
        qualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(qualifiedName);
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
        baseInterface = GitLabEndpointUseCaseModelSupport.trimToNull(baseInterface);
        entityType = GitLabEndpointUseCaseModelSupport.trimToNull(entityType);
        idType = GitLabEndpointUseCaseModelSupport.trimToNull(idType);
        declaredMethodNames = GitLabEndpointUseCaseModelSupport.copyStrings(declaredMethodNames);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
