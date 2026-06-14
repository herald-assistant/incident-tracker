package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaBeanCandidate(
        String simpleName,
        String relativeName,
        String qualifiedName,
        GitLabJavaTypeKind kind,
        String filePath,
        List<String> stereotypeAnnotations,
        boolean potentialBean,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaBeanCandidate {
        simpleName = GitLabEndpointUseCaseModelSupport.trimToNull(simpleName);
        relativeName = GitLabEndpointUseCaseModelSupport.trimToNull(relativeName);
        qualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(qualifiedName);
        kind = kind != null ? kind : GitLabJavaTypeKind.UNKNOWN;
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        stereotypeAnnotations = GitLabEndpointUseCaseModelSupport.copyStrings(stereotypeAnnotations);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
