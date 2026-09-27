package pl.mkn.tdw.integrations.gitlab.contract.frontend;

public record GitLabFrontendTechnicalSignal(
        GitLabFrontendTechnicalSignalKind kind,
        String description,
        GitLabFrontendSignalConfidence confidence,
        GitLabFrontendSourceReference source
) {
}

