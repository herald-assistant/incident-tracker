package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;

public interface UiExplorerLocalRunPersistence {

    UiExplorerLocalRunPersistence NO_OP = snapshot -> {
    };

    void persistTerminalSnapshot(UiExplorerJobStateSnapshot snapshot);
}
