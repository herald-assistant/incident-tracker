package pl.mkn.incidenttracker.api.gitlab;

public record GitLabRepositoryFileByPathApiResult(
        String group,
        String projectName,
        String branch,
        String filePath,
        String content,
        boolean truncated,
        String inferredRole,
        int returnedCharacters,
        String error
) {
}
