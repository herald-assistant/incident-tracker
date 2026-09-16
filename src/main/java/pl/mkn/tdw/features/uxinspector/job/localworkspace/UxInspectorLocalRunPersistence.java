package pl.mkn.tdw.features.uxinspector.job.localworkspace;

import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStateSnapshot;

@FunctionalInterface
public interface UxInspectorLocalRunPersistence {
    UxInspectorLocalRunPersistence NO_OP = snapshot -> {};
    void persistRunSnapshot(UxInspectorJobStateSnapshot snapshot);
}

