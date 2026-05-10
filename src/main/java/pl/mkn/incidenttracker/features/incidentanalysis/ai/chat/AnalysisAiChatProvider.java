package pl.mkn.incidenttracker.features.incidentanalysis.ai.chat;

import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityListener;
import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;

public interface AnalysisAiChatProvider {

    AnalysisAiChatResponse chat(
            AnalysisAiChatRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    );

    default AnalysisAiChatResponse chat(
            AnalysisAiChatRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        return chat(request, toolEvidenceListener);
    }

}
