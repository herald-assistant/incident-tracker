package pl.mkn.incidenttracker.analysis.evidence;

import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

public interface AnalysisEvidenceCollectionListener {

    AnalysisEvidenceCollectionListener NO_OP = new AnalysisEvidenceCollectionListener() {
    };

    default void onProviderStarted(AnalysisEvidenceProvider provider, AnalysisContext context) {
    }

    default void onProviderCompleted(
            AnalysisEvidenceProvider provider,
            AnalysisEvidenceSection section,
            AnalysisContext updatedContext
    ) {
    }

    default void onProviderFailed(
            AnalysisEvidenceProvider provider,
            RuntimeException exception,
            AnalysisContext context
    ) {
    }

}
