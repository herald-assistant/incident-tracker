package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public record DeliveryUnitAiAnalysis(
        DeliveryAiResponse response,
        AnalysisAiUsage usage,
        String prompt,
        String sessionId
) {
}
