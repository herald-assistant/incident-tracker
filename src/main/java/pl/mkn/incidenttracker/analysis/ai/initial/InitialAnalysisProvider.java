package pl.mkn.incidenttracker.analysis.ai.initial;

import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;

public interface InitialAnalysisProvider {

    InitialAnalysisPreparation prepare(InitialAnalysisRequest request);

    InitialAnalysisResponse analyze(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    );

}
