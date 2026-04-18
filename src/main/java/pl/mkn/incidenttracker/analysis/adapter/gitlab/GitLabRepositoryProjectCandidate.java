package pl.mkn.incidenttracker.analysis.adapter.gitlab;

public record GitLabRepositoryProjectCandidate(
        String group,
        String projectPath,
        String matchReason,
        int matchScore
) {
}
