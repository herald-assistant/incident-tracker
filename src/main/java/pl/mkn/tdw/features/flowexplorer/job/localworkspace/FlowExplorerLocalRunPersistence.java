package pl.mkn.tdw.features.flowexplorer.job.localworkspace;

import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@FunctionalInterface
public interface FlowExplorerLocalRunPersistence {

    FlowExplorerLocalRunPersistence NO_OP = (snapshot, authRef, copilotSessionId) -> {
    };

    void persistCompletedInitialRun(
            FlowExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef,
            String copilotSessionId
    );

    default void persistCompletedInitialRun(
            FlowExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef
    ) {
        persistCompletedInitialRun(snapshot, authRef, null);
    }
}
