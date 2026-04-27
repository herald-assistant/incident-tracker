package pl.mkn.incidenttracker.analysis.ai;

public interface AnalysisAiChatProvider {

    AnalysisAiChatResponse chat(
            AnalysisAiChatRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    );

}
