package pl.mkn.tdw.features.changeverification.ai;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVerificationCheckResponse;

import java.util.List;

public record ChangeVerificationAiResponse(
        String status,
        List<ChangeVerificationVerificationCheckResponse> verificationChecks,
        List<ChangeVerificationFindingResponse> findings,
        List<String> suggestedActions,
        List<String> visibilityLimits,
        String confidence
) {

    public ChangeVerificationAiResponse {
        verificationChecks = verificationChecks != null ? List.copyOf(verificationChecks) : List.of();
        findings = findings != null ? List.copyOf(findings) : List.of();
        suggestedActions = suggestedActions != null ? List.copyOf(suggestedActions) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
