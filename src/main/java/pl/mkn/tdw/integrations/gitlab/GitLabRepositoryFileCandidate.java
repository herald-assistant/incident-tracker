package pl.mkn.tdw.integrations.gitlab;

public record GitLabRepositoryFileCandidate(
        String group,
        String projectName,
        String branch,
        String filePath,
        String matchReason,
        int matchScore
) {
}
