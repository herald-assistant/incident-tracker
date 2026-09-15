package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationRuleLedgerResponse(
        boolean storyComplianceRequested,
        boolean instructionComplianceRequested,
        ChangeVerificationDecisionResponse decision,
        List<ChangeVerificationRuleResultResponse> rules,
        List<ChangeVerificationRuleResultResponse> additionalChecks,
        List<ChangeVerificationVisibilityLimitResponse> visibilityLimits
) {
    public ChangeVerificationRuleLedgerResponse {
        rules = rules != null ? List.copyOf(rules) : List.of();
        additionalChecks = additionalChecks != null ? List.copyOf(additionalChecks) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        decision = ChangeVerificationDecisionResponse.from(rules);
    }
}
