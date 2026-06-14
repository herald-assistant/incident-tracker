package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseContextRequest(
        String projectName,
        String endpointId,
        String httpMethod,
        String endpointPath,
        String sourcePathPrefix,
        Integer maxDepth,
        Integer maxFiles,
        String reason
) {
    public static final String DEFAULT_SOURCE_PATH_PREFIX = "src/main/java";
    public static final int DEFAULT_MAX_DEPTH = 5;
    public static final int MAX_MAX_DEPTH = 8;
    public static final int DEFAULT_MAX_FILES = 25;
    public static final int MAX_MAX_FILES = 40;

    public GitLabEndpointUseCaseContextRequest {
        projectName = GitLabEndpointUseCaseModelSupport.trimToNull(projectName);
        endpointId = GitLabEndpointUseCaseModelSupport.trimToNull(endpointId);
        httpMethod = GitLabEndpointUseCaseModelSupport.normalizeHttpMethod(httpMethod);
        endpointPath = GitLabEndpointUseCaseModelSupport.normalizeEndpointPath(endpointPath);
        sourcePathPrefix = GitLabEndpointUseCaseModelSupport.normalizeSourcePathPrefix(sourcePathPrefix);
        maxDepth = GitLabEndpointUseCaseModelSupport.normalizeLimit(maxDepth, DEFAULT_MAX_DEPTH, MAX_MAX_DEPTH);
        maxFiles = GitLabEndpointUseCaseModelSupport.normalizeLimit(maxFiles, DEFAULT_MAX_FILES, MAX_MAX_FILES);
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
    }
}
