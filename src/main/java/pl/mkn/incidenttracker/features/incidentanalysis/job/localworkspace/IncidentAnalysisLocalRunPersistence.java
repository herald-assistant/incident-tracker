package pl.mkn.incidenttracker.features.incidentanalysis.job.localworkspace;

import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;

@FunctionalInterface
public interface IncidentAnalysisLocalRunPersistence {

    IncidentAnalysisLocalRunPersistence NO_OP = (snapshot, aiRequest) -> {
    };

    void persistCompletedInitialRun(
            AnalysisJobStateSnapshot snapshot,
            InitialAnalysisRequest aiRequest
    );
}
