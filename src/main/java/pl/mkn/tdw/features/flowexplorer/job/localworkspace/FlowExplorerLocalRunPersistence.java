package pl.mkn.tdw.features.flowexplorer.job.localworkspace;

import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@FunctionalInterface
public interface FlowExplorerLocalRunPersistence {

    FlowExplorerLocalRunPersistence NO_OP = (snapshot, authRef, copilotSessionId) -> {
    };

    void persistRunSnapshot(
            FlowExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef,
            String copilotSessionId
    );

    default void persistRunSnapshot(FlowExplorerJobStateSnapshot snapshot) {
        persistRunSnapshot(snapshot, null, null);
    }

    default void persistRunSnapshot(
            FlowExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef
    ) {
        persistRunSnapshot(snapshot, authRef, null);
    }

    default void persistCompletedInitialRun(
            FlowExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef,
            String copilotSessionId
    ) {
        persistRunSnapshot(snapshot, authRef, copilotSessionId);
    }

    default void persistCompletedInitialRun(
            FlowExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef
    ) {
        persistRunSnapshot(snapshot, authRef, null);
    }
}
