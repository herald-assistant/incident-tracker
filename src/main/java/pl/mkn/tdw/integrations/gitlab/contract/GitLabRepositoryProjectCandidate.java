package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabRepositoryProjectCandidate(
        String group,
        String projectPath,
        String matchReason,
        int matchScore
) {
}
