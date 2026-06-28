package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaMethodUseCaseContextRequest(
        String projectName,
        String filePath,
        String className,
        String methodName,
        Integer lineNumber,
        Integer parameterCount,
        List<String> parameterTypes,
        Integer maxDepth,
        Integer maxResults
) {
    public static final int DEFAULT_MAX_DEPTH = GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH;
    public static final int MAX_MAX_DEPTH = GitLabEndpointUseCaseContextRequest.MAX_MAX_DEPTH;
    public static final int DEFAULT_MAX_RESULTS = 40;
    public static final int MAX_MAX_RESULTS = 100;

    public GitLabJavaMethodUseCaseContextRequest {
        projectName = GitLabEndpointUseCaseModelSupport.trimToNull(projectName);
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        className = GitLabEndpointUseCaseModelSupport.trimToNull(className);
        methodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        lineNumber = lineNumber != null && lineNumber > 0 ? lineNumber : null;
        if (parameterCount != null && parameterCount < 0) {
            parameterCount = null;
        }
        parameterTypes = GitLabEndpointUseCaseModelSupport.copyStrings(parameterTypes);
        maxDepth = GitLabEndpointUseCaseModelSupport.normalizeLimit(maxDepth, DEFAULT_MAX_DEPTH, MAX_MAX_DEPTH);
        maxResults = GitLabEndpointUseCaseModelSupport.normalizeLimit(maxResults, DEFAULT_MAX_RESULTS, MAX_MAX_RESULTS);
    }
}
