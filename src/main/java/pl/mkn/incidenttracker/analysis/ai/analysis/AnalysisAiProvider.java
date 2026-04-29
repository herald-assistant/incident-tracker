package pl.mkn.incidenttracker.analysis.ai.analysis;

import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.prepared.AnalysisAiPreparedAnalysis;

public interface AnalysisAiProvider {

    AnalysisAiPreparedAnalysis prepare(AnalysisAiAnalysisRequest request);

    AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request);

    AnalysisAiAnalysisResponse analyze(
            AnalysisAiPreparedAnalysis preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    );

}

