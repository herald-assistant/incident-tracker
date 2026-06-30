package pl.mkn.tdw.features.incidentanalysis.job.localworkspace;

import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;

public interface IncidentAnalysisLocalRunPersistence {

    IncidentAnalysisLocalRunPersistence NO_OP = new IncidentAnalysisLocalRunPersistence() {
    };

    default void persistRunSnapshot(
            AnalysisJobStateSnapshot snapshot,
            InitialAnalysisRequest aiRequest,
            String copilotSessionId
    ) {
    }

    default void persistRunSnapshot(AnalysisJobStateSnapshot snapshot) {
        persistRunSnapshot(snapshot, null, null);
    }

    default void persistCompletedInitialRun(
            AnalysisJobStateSnapshot snapshot,
            InitialAnalysisRequest aiRequest
    ) {
        persistCompletedInitialRun(snapshot, aiRequest, null);
    }

    default void persistCompletedInitialRun(
            AnalysisJobStateSnapshot snapshot,
            InitialAnalysisRequest aiRequest,
            String copilotSessionId
    ) {
        persistRunSnapshot(snapshot, aiRequest, copilotSessionId);
    }
}
