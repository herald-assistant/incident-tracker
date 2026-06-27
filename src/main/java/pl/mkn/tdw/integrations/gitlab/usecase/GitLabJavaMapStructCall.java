package pl.mkn.tdw.integrations.gitlab.usecase;

public record GitLabJavaMapStructCall(
        GitLabJavaMapStructCallKind kind,
        String mapperType,
        String methodName,
        String sourceExpression,
        String filePath,
        int line,
        boolean switchBranchCandidate,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaMapStructCall {
        kind = kind != null ? kind : GitLabJavaMapStructCallKind.INSTANCE_METHOD;
        mapperType = GitLabEndpointUseCaseModelSupport.trimToNull(mapperType);
        methodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        sourceExpression = GitLabEndpointUseCaseModelSupport.trimToNull(sourceExpression);
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        line = Math.max(0, line);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
