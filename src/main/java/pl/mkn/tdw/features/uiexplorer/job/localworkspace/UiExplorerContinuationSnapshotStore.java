package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UiExplorerContinuationSnapshotStore {

    private static final String FILE_NAME = "ui-explorer-continuation.json";

    private final LocalWorkspacePaths paths;
    private final LocalWorkspaceJsonFileStore jsonFileStore;

    public void save(UiExplorerContinuationSnapshot snapshot) {
        jsonFileStore.writeAtomic(path(snapshot.jobId()), snapshot);
    }

    public Optional<UiExplorerContinuationSnapshot> findById(String jobId) {
        return jsonFileStore.read(path(jobId), UiExplorerContinuationSnapshot.class)
                .filter(snapshot -> UiExplorerContinuationSnapshot.SCHEMA.equals(snapshot.schema()))
                .filter(snapshot -> snapshot.version() == UiExplorerContinuationSnapshot.VERSION)
                .filter(snapshot -> jobId.equals(snapshot.jobId()));
    }

    private java.nio.file.Path path(String jobId) {
        return paths.runDirectory(jobId).resolve(FILE_NAME);
    }
}
