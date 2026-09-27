package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabRepositoryFileCandidate(
        String group,
        String projectName,
        String branch,
        String filePath,
        String matchReason,
        int matchScore
) {
}
