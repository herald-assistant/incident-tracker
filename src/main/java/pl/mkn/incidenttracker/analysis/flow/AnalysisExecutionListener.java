package pl.mkn.incidenttracker.analysis.flow;

import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
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

}
