package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendUseCaseRelation(
        String from,
        String to,
        GitLabFrontendUseCaseRelationKind kind,
        String symbol,
        GitLabFrontendSignalConfidence confidence,
        GitLabFrontendSourceReference source
) {
    public GitLabFrontendUseCaseRelation {
        confidence = confidence != null ? confidence : GitLabFrontendSignalConfidence.LOW;
    }
}
