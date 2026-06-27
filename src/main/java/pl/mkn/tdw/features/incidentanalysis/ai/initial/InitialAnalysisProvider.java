package pl.mkn.tdw.features.incidentanalysis.ai.initial;

import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

public interface InitialAnalysisProvider {

    InitialAnalysisPreparation prepare(InitialAnalysisRequest request);

    InitialAnalysisResponse analyze(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    );

    default InitialAnalysisResponse analyze(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        return analyze(preparedAnalysis, toolEvidenceListener);
    }

}
