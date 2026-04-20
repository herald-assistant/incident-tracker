package pl.mkn.incidenttracker.analysis.ai;

public interface AnalysisAiProvider {

    default String preparePrompt(AnalysisAiAnalysisRequest request) {
        return null;
    }

    default AnalysisAiAnalysisResponse analyze(
            AnalysisAiAnalysisRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        return analyze(request);
    }

    AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request);

}
