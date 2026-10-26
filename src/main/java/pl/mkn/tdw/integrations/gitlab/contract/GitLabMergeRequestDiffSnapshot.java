package pl.mkn.tdw.integrations.gitlab.contract;

import java.util.List;
import java.util.regex.Pattern;

/** One immutable GitLab diff version. base is the merge base; start is the target tip. */
public record GitLabMergeRequestDiffSnapshot(
        String projectPath,
        long mergeRequestIid,
        Long versionId,
        String baseSha,
        String startSha,
        String headSha,
        List<FileDiff> files,
        List<String> limitations
) {
    private static final Pattern SHA = Pattern.compile("(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})");

    public GitLabMergeRequestDiffSnapshot {
        files = files == null ? List.of() : List.copyOf(files);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public boolean hasRevisions() {
        return validSha(baseSha) && validSha(startSha) && validSha(headSha) && versionId != null;
    }

    private static boolean validSha(String value) {
        return value != null && SHA.matcher(value).matches();
    }

    public record FileDiff(String oldPath, String newPath, boolean newFile, boolean renamedFile,
                           boolean deletedFile, String patch, boolean collapsed, boolean tooLarge) {
    }
}
