package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record DeliveryUnitAiAnalysis(
        DeliveryAiResponse response,
        AnalysisAiUsage usage,
        String prompt,
        String sessionId,
        AnalysisReport report
) {
}
