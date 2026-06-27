package pl.mkn.tdw.integrations.gitlab;

public record GitLabRepositoryProjectCandidate(
        String group,
        String projectPath,
        String matchReason,
        int matchScore
) {
}
