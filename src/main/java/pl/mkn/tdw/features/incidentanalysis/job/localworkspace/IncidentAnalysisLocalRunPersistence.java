package pl.mkn.tdw.features.incidentanalysis.job.localworkspace;

import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;

@FunctionalInterface
public interface IncidentAnalysisLocalRunPersistence {

    IncidentAnalysisLocalRunPersistence NO_OP = (snapshot, aiRequest, copilotSessionId) -> {
    };

    void persistCompletedInitialRun(
            AnalysisJobStateSnapshot snapshot,
            InitialAnalysisRequest aiRequest,
            String copilotSessionId
    );

    default void persistCompletedInitialRun(
            AnalysisJobStateSnapshot snapshot,
            InitialAnalysisRequest aiRequest
    ) {
        persistCompletedInitialRun(snapshot, aiRequest, null);
    }
}
