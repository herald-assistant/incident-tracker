package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseEdge(
        String from,
        String to,
        GitLabEndpointUseCaseEdgeKind kind,
        GitLabEndpointUseCaseResolutionKind resolutionKind,
        String call,
        Integer line,
        GitLabEndpointUseCaseConfidence confidence,
        boolean ambiguous
) {
    public GitLabEndpointUseCaseEdge {
        kind = kind != null ? kind : GitLabEndpointUseCaseEdgeKind.SYNC_CALL;
        resolutionKind = resolutionKind != null ? resolutionKind : GitLabEndpointUseCaseResolutionKind.UNRESOLVED;
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.MEDIUM;
    }
}
