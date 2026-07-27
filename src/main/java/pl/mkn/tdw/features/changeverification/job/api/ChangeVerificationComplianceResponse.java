package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationComplianceResponse(
        boolean storyComplianceRequested,
        boolean instructionComplianceRequested,
        String status,
        List<ChangeVerificationVerificationCheckResponse> verificationChecks,
        List<ChangeVerificationFindingResponse> findings,
        List<String> suggestedActions,
        List<String> visibilityLimits
) {

    public ChangeVerificationComplianceResponse {
        verificationChecks = verificationChecks != null ? List.copyOf(verificationChecks) : List.of();
        findings = findings != null ? List.copyOf(findings) : List.of();
        suggestedActions = suggestedActions != null ? List.copyOf(suggestedActions) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
