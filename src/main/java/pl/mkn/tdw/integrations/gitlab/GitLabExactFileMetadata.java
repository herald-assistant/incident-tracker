package pl.mkn.tdw.integrations.gitlab;

public record GitLabExactFileMetadata(
        String connectionId,
        String projectPath,
        String ref,
        String filePath,
        String blobId,
        String commitId,
        String lastCommitId,
        String lastModifiedAt,
        String contentSha256,
        Long sizeBytes
) {
}
