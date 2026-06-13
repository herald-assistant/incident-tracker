package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseLimits(
        int maxDepth,
        int maxNodes,
        boolean maxDepthReached,
        boolean maxNodesReached
) {
    public static GitLabEndpointUseCaseLimits defaults() {
        return new GitLabEndpointUseCaseLimits(
                GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH,
                GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_NODES,
                false,
                false
        );
    }
}
