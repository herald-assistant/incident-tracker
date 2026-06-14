package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseTraversalNode(
        String filePath,
        String typeName,
        String methodName,
        Integer argumentCount,
        List<String> parameterTypes,
        int depth,
        GitLabEndpointUseCaseFileRole role,
        GitLabEndpointUseCaseConfidence confidence,
        String reason
) {
    GitLabEndpointUseCaseTraversalNode(
            String filePath,
            String typeName,
            String methodName,
            Integer argumentCount,
            int depth,
            GitLabEndpointUseCaseFileRole role,
            GitLabEndpointUseCaseConfidence confidence,
            String reason
    ) {
        this(filePath, typeName, methodName, argumentCount, List.of(), depth, role, confidence, reason);
    }

    GitLabEndpointUseCaseTraversalNode {
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        typeName = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        methodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        if (argumentCount != null && argumentCount < 0) {
            argumentCount = null;
        }
        parameterTypes = GitLabEndpointUseCaseModelSupport.copyStrings(parameterTypes);
        depth = Math.max(0, depth);
        role = role != null ? role : GitLabEndpointUseCaseFileRole.UNKNOWN;
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
    }

    String key() {
        return "%s|%s|%s|%s|%s".formatted(
                filePath,
                typeName,
                methodName,
                argumentCount != null ? argumentCount : "*",
                parameterTypes.isEmpty() ? "*" : String.join(",", parameterTypes)
        );
    }
}
