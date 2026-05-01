package pl.mkn.incidenttracker.features.incidentanalysis.evidence;

import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

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

}
