package pl.mkn.incidenttracker.analysis.ai;

public interface AnalysisAiProvider {

    default String preparePrompt(AnalysisAiAnalysisRequest request) {
        return null;
    }

    AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request);

}
