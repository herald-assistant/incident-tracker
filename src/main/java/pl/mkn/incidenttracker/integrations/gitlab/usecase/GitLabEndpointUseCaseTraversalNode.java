package pl.mkn.incidenttracker.integrations.gitlab.usecase;

record GitLabEndpointUseCaseTraversalNode(
        String filePath,
        String typeName,
        String methodName,
        Integer argumentCount,
        int depth,
        GitLabEndpointUseCaseFileRole role,
        GitLabEndpointUseCaseConfidence confidence,
        String reason
) {
    GitLabEndpointUseCaseTraversalNode {
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        typeName = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        methodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        if (argumentCount != null && argumentCount < 0) {
            argumentCount = null;
        }
        depth = Math.max(0, depth);
        role = role != null ? role : GitLabEndpointUseCaseFileRole.UNKNOWN;
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
    }

    String key() {
        return "%s|%s|%s|%s".formatted(
                filePath,
                typeName,
                methodName,
                argumentCount != null ? argumentCount : "*"
        );
    }
}
