package pl.mkn.tdw.integrations.gitlab;

public record GitLabMergeRequestChangedFile(
        String oldPath,
        String newPath,
        boolean newFile,
        boolean renamedFile,
        boolean deletedFile
) {
}
