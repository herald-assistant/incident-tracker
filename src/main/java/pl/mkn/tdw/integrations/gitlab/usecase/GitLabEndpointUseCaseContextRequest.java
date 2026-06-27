package pl.mkn.tdw.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseContextRequest(
        String projectName,
        String endpointId,
        String httpMethod,
        String endpointPath,
        Integer maxDepth,
        Integer maxFiles
) {
    public static final int DEFAULT_MAX_DEPTH = 5;
    public static final int MAX_MAX_DEPTH = 8;
    public static final int DEFAULT_MAX_FILES = 60;
    public static final int MAX_MAX_FILES = 100;

    public GitLabEndpointUseCaseContextRequest {
        projectName = GitLabEndpointUseCaseModelSupport.trimToNull(projectName);
        endpointId = GitLabEndpointUseCaseModelSupport.trimToNull(endpointId);
        httpMethod = GitLabEndpointUseCaseModelSupport.normalizeHttpMethod(httpMethod);
        endpointPath = GitLabEndpointUseCaseModelSupport.normalizeEndpointPath(endpointPath);
        maxDepth = GitLabEndpointUseCaseModelSupport.normalizeLimit(maxDepth, DEFAULT_MAX_DEPTH, MAX_MAX_DEPTH);
        maxFiles = GitLabEndpointUseCaseModelSupport.normalizeLimit(maxFiles, DEFAULT_MAX_FILES, MAX_MAX_FILES);
    }
}
