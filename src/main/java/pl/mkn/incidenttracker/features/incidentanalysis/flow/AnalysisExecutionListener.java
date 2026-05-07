package pl.mkn.incidenttracker.features.incidentanalysis.flow;

import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.AnalysisEvidenceCollectionListener;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

/**
 * Receives high-level events from the full incident analysis flow.
 *
 * <p>The listener extends deterministic evidence collection events and adds AI
 * execution events emitted by {@link AnalysisOrchestrator}. Implementations
 * should treat these callbacks as notifications from the flow and adapt them to
 * a concrete projection, telemetry sink, or test assertion without moving
 * orchestration logic out of the flow package.</p>
 */
public interface AnalysisExecutionListener extends AnalysisEvidenceCollectionListener {

    AnalysisExecutionListener NO_OP = new AnalysisExecutionListener() {
    };

    default void onAiStarted(InitialAnalysisRequest request, AnalysisContext context) {
    }

    default void onAiPromptPrepared(
            InitialAnalysisRequest request,
            String preparedPrompt,
            AnalysisContext context
    ) {
    }

    default void onAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
    }

    default void onAiActivity(AnalysisAiActivityEvent event) {
    }

}
