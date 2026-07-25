package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationComplianceResponse(
        boolean storyComplianceRequested,
        boolean instructionComplianceRequested,
        String status,
        List<ChangeVerificationFindingResponse> findings,
        List<String> suggestedActions,
        List<String> visibilityLimits
) {

    public ChangeVerificationComplianceResponse {
        findings = findings != null ? List.copyOf(findings) : List.of();
        suggestedActions = suggestedActions != null ? List.copyOf(suggestedActions) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
