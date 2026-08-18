package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import pl.mkn.tdw.features.deliverycomplexityassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

public interface DeliveryUnitAssessmentProvider {

    DeliveryUnitAiAnalysis analyze(
            String runReference,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef,
            DeliveryEvidencePacket packet,
            DeliveryPromptPreparation preparation,
            AnalysisAiActivityListener activityListener
    );
}
