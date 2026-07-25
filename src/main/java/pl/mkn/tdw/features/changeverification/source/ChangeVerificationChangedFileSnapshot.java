package pl.mkn.tdw.features.changeverification.source;

import java.util.List;

public record ChangeVerificationChangedFileSnapshot(
        String path,
        String oldPath,
        String newPath,
        boolean newFile,
        boolean renamedFile,
        boolean deletedFile,
        List<String> mergeRequestRefs
) {

    public ChangeVerificationChangedFileSnapshot {
        mergeRequestRefs = mergeRequestRefs != null ? List.copyOf(mergeRequestRefs) : List.of();
    }
}
