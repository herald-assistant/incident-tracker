package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseRelation(
        String from,
        String to,
        GitLabEndpointUseCaseRelationKind kind,
        GitLabEndpointUseCaseConfidence confidence,
        String reason
) {
    public GitLabEndpointUseCaseRelation {
        from = GitLabEndpointUseCaseModelSupport.trimToNull(from);
        to = GitLabEndpointUseCaseModelSupport.trimToNull(to);
        kind = kind != null ? kind : GitLabEndpointUseCaseRelationKind.UNKNOWN;
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
    }
}
