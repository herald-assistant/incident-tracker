package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendTechnicalSignal(
        GitLabFrontendTechnicalSignalKind kind,
        String description,
        GitLabFrontendSignalConfidence confidence,
        GitLabFrontendSourceReference source
) {
}

