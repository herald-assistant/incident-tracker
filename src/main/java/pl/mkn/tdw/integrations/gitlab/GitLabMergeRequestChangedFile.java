package pl.mkn.tdw.integrations.gitlab;

public record GitLabMergeRequestChangedFile(
        String oldPath,
        String newPath,
        boolean newFile,
        boolean renamedFile,
        boolean deletedFile,
        String diff
) {

    public GitLabMergeRequestChangedFile(
            String oldPath,
            String newPath,
            boolean newFile,
            boolean renamedFile,
            boolean deletedFile
    ) {
        this(oldPath, newPath, newFile, renamedFile, deletedFile, null);
    }
}
