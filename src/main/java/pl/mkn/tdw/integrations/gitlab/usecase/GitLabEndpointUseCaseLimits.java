package pl.mkn.tdw.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseLimits(
        int maxDepth,
        int maxFiles,
        int maxReadFiles,
        boolean maxDepthReached,
        boolean maxFilesReached,
        int readFileCount,
        boolean readFileLimitReached
) {
    public static final int DEFAULT_MAX_READ_FILES = 60;

    public GitLabEndpointUseCaseLimits {
        maxDepth = normalize(maxDepth, GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH,
                GitLabEndpointUseCaseContextRequest.MAX_MAX_DEPTH);
        maxFiles = normalize(maxFiles, GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_FILES,
                GitLabEndpointUseCaseContextRequest.MAX_MAX_FILES);
        maxReadFiles = maxReadFiles < 1 ? DEFAULT_MAX_READ_FILES : maxReadFiles;
        readFileCount = Math.max(0, readFileCount);
    }

    public static GitLabEndpointUseCaseLimits defaults() {
        return new GitLabEndpointUseCaseLimits(
                GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH,
                GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_FILES,
                DEFAULT_MAX_READ_FILES,
                false,
                false,
                0,
                false
        );
    }

    public static GitLabEndpointUseCaseLimits forRequest(GitLabEndpointUseCaseContextRequest request) {
        if (request == null) {
            return defaults();
        }
        return new GitLabEndpointUseCaseLimits(
                request.maxDepth(),
                request.maxFiles(),
                DEFAULT_MAX_READ_FILES,
                false,
                false,
                0,
                false
        );
    }

    private static int normalize(int value, int defaultValue, int maxValue) {
        if (value < 1) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
