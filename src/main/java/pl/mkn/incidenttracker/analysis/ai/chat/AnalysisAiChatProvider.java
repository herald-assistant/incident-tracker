package pl.mkn.incidenttracker.analysis.ai.chat;

import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;

public interface AnalysisAiChatProvider {

    AnalysisAiChatResponse chat(
            AnalysisAiChatRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    );

}

