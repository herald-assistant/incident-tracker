package pl.mkn.tdw.features.changeverification.ai;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public record ChangeVerificationSmokePackAnalysis(
        ChangeVerificationSmokePackResponse response,
        AnalysisAiUsage usage,
        String prompt,
        String sessionId
) {
}
