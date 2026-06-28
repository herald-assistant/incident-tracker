package pl.mkn.tdw.integrations.gitlab.usecase;

public record GitLabJavaMethodUseCaseContextLimits(
        int maxDepth,
        int maxResults,
        int maxReadFiles,
        boolean maxDepthReached,
        boolean maxResultsReached,
        int readFileCount,
        boolean readFileLimitReached
) {
    public GitLabJavaMethodUseCaseContextLimits {
        maxDepth = normalize(
                maxDepth,
                GitLabJavaMethodUseCaseContextRequest.DEFAULT_MAX_DEPTH,
                GitLabJavaMethodUseCaseContextRequest.MAX_MAX_DEPTH
        );
        maxResults = normalize(
                maxResults,
                GitLabJavaMethodUseCaseContextRequest.DEFAULT_MAX_RESULTS,
                GitLabJavaMethodUseCaseContextRequest.MAX_MAX_RESULTS
        );
        maxReadFiles = maxReadFiles < 1 ? GitLabEndpointUseCaseLimits.DEFAULT_MAX_READ_FILES : maxReadFiles;
        readFileCount = Math.max(0, readFileCount);
    }

    public static GitLabJavaMethodUseCaseContextLimits defaults() {
        return new GitLabJavaMethodUseCaseContextLimits(
                GitLabJavaMethodUseCaseContextRequest.DEFAULT_MAX_DEPTH,
                GitLabJavaMethodUseCaseContextRequest.DEFAULT_MAX_RESULTS,
                GitLabEndpointUseCaseLimits.DEFAULT_MAX_READ_FILES,
                false,
                false,
                0,
                false
        );
    }

    public static GitLabJavaMethodUseCaseContextLimits forRequest(GitLabJavaMethodUseCaseContextRequest request) {
        if (request == null) {
            return defaults();
        }
        return new GitLabJavaMethodUseCaseContextLimits(
                request.maxDepth(),
                request.maxResults(),
                GitLabEndpointUseCaseLimits.DEFAULT_MAX_READ_FILES,
                false,
                false,
                0,
                false
        );
    }

    public static GitLabJavaMethodUseCaseContextLimits fromSession(
            GitLabJavaMethodUseCaseContextRequest request,
            GitLabEndpointUseCaseSourceSession session,
            boolean maxDepthReached,
            boolean maxResultsReached
    ) {
        var base = forRequest(request);
        return new GitLabJavaMethodUseCaseContextLimits(
                base.maxDepth(),
                base.maxResults(),
                session != null ? session.maxReadFiles() : base.maxReadFiles(),
                maxDepthReached,
                maxResultsReached,
                session != null ? session.readFileCount() : base.readFileCount(),
                session != null && session.readFileLimitReached()
        );
    }

    public static GitLabJavaMethodUseCaseContextLimits fromEndpointLimits(GitLabEndpointUseCaseLimits limits) {
        if (limits == null) {
            return defaults();
        }
        return new GitLabJavaMethodUseCaseContextLimits(
                limits.maxDepth(),
                limits.maxFiles(),
                limits.maxReadFiles(),
                limits.maxDepthReached(),
                limits.maxFilesReached(),
                limits.readFileCount(),
                limits.readFileLimitReached()
        );
    }

    private static int normalize(int value, int defaultValue, int maxValue) {
        if (value < 1) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
