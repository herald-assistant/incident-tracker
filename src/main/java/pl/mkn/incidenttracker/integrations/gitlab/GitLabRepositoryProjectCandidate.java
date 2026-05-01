package pl.mkn.incidenttracker.integrations.gitlab;

public record GitLabRepositoryProjectCandidate(
        String group,
        String projectPath,
        String matchReason,
        int matchScore
) {
}
