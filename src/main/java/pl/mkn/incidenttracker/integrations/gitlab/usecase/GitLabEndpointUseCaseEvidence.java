package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseEvidence(
        String kind,
        String message,
        String sourcePath,
        Integer line
) {
}
