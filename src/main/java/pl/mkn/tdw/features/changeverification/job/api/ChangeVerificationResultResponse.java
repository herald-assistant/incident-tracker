package pl.mkn.tdw.features.changeverification.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public record ChangeVerificationResultResponse(
        String status,
        String issueKey,
        String issueUrl,
        String prompt,
        ChangeVerificationComplianceResponse compliance,
        AnalysisAiUsage usage
) {
}
