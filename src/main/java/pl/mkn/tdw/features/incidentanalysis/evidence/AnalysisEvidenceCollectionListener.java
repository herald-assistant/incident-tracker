package pl.mkn.tdw.features.incidentanalysis.evidence;

import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

/**
 * Receives deterministic evidence collection progress for a single incident
 * analysis execution.
 *
 * <p>This contract belongs to the evidence pipeline only. It reports provider
 * lifecycle events and should not know about AI execution, jobs, UI state, or
 * transport concerns.</p>
 */
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
