package pl.mkn.incidenttracker.analysis.flow;

import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;

public interface AnalysisExecutionListener {

    AnalysisExecutionListener NO_OP = new AnalysisExecutionListener() {
    };

    default void onProviderStarted(AnalysisEvidenceProvider provider, AnalysisContext context) {
    }

    default void onProviderCompleted(
            AnalysisEvidenceProvider provider,
            AnalysisEvidenceSection section,
            AnalysisContext updatedContext
    ) {
    }

    default void onAiStarted(AnalysisAiAnalysisRequest request, AnalysisContext context) {
    }

    default void onAiPromptPrepared(
            AnalysisAiAnalysisRequest request,
            String preparedPrompt,
            AnalysisContext context
    ) {
    }

    default void onAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
    }

}
