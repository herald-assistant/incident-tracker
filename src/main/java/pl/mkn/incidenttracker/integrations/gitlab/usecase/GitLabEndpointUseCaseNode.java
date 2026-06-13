package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseNode(
        String id,
        GitLabEndpointUseCaseNodeKind kind,
        String classFqn,
        String methodSignature,
        GitLabEndpointUseCaseRole role,
        int depth,
        String sourcePath,
        int lineStart,
        int lineEnd,
        boolean terminal,
        String terminalReason
) {
    public GitLabEndpointUseCaseNode {
        kind = kind != null ? kind : GitLabEndpointUseCaseNodeKind.METHOD;
        role = role != null ? role : GitLabEndpointUseCaseRole.UNKNOWN;
    }
}
