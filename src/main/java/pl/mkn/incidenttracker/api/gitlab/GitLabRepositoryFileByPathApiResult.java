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
        Long sizeBytes,
        String contentSha256,
        String blobId,
        String commitId,
        String lastCommitId,
        String lastModifiedAt,
        String metadataStatus,
        String metadataError,
        String error
) {
}
