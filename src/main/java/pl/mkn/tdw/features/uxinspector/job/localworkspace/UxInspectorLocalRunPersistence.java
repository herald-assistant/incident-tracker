package pl.mkn.tdw.features.uxinspector.job.localworkspace;

import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStateSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStoreSnapshot;

@FunctionalInterface
public interface UxInspectorLocalRunPersistence {
    UxInspectorLocalRunPersistence NO_OP = snapshot -> {};
    void persistRunSnapshot(UxInspectorJobStateSnapshot snapshot);
    default void persistStoreSnapshot(String runId, LocalAnalysisRunStoreSnapshot snapshot) {}
    default void persistRunSnapshot(UxInspectorJobStateSnapshot snapshot, AnalysisAiAuthRef authRef, String copilotSessionId) {
        persistRunSnapshot(snapshot);
    }
}
