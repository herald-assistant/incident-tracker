package pl.mkn.tdw.features.changeverification.ai;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record ChangeVerificationComplianceAnalysis(
        ChangeVerificationAiResponse response,
        AnalysisAiUsage usage,
        String prompt,
        String copilotSessionId,
        AnalysisReport report
) {
}
