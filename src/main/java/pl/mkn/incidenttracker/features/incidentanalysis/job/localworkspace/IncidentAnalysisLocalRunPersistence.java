package pl.mkn.incidenttracker.features.incidentanalysis.job.localworkspace;

import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;

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
