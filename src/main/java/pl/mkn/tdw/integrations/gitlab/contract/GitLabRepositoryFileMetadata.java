package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabRepositoryFileMetadata(
        String group,
        String projectName,
        String branch,
        String filePath,
        String blobId,
        String commitId,
        String lastCommitId,
        String lastModifiedAt,
        String contentSha256,
        Long sizeBytes
) {
}
