package pl.mkn.incidenttracker.analysis.ai;

public interface AnalysisAiProvider {

    AnalysisAiPreparedAnalysis prepare(AnalysisAiAnalysisRequest request);

    AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request);

    AnalysisAiAnalysisResponse analyze(
            AnalysisAiPreparedAnalysis preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    );

}
