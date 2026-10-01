package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import pl.mkn.tdw.features.deliveryscopecomplexity.evidence.DeliveryEvidencePacket;
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
            AnalysisAiActivityListener activityListener,
            DeliveryRawAiResponseListener rawResponseListener
    );
}
