package pl.mkn.tdw.features.changeverification.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.List;

public record ChangeVerificationResultResponse(
        String status,
        String issueKey,
        String issueUrl,
        List<ChangeVerificationJobMode> modes,
        String prompt,
        ChangeVerificationComplianceResponse compliance,
        ChangeVerificationSmokePackResponse smokePack,
        ChangeVerificationExecutionResponse execution,
        AnalysisAiUsage usage
) {

    public ChangeVerificationResultResponse {
        modes = modes != null ? List.copyOf(modes) : List.of();
    }
}
