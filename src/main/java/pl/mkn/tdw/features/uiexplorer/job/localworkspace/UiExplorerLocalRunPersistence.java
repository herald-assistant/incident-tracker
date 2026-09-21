package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

public interface UiExplorerLocalRunPersistence {

    UiExplorerLocalRunPersistence NO_OP = new UiExplorerLocalRunPersistence() {
        @Override public void persistTerminalSnapshot(UiExplorerJobStateSnapshot snapshot) { }
        @Override public void persistRunSnapshot(UiExplorerJobStateSnapshot snapshot, AnalysisAiAuthRef authRef,
                                                 String copilotSessionId, UiExplorerScreenReachabilityContext context) { }
    };

    void persistTerminalSnapshot(UiExplorerJobStateSnapshot snapshot);

    default void persistRunSnapshot(
            UiExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef,
            String copilotSessionId,
            UiExplorerScreenReachabilityContext context
    ) {
        persistTerminalSnapshot(snapshot);
    }
}
