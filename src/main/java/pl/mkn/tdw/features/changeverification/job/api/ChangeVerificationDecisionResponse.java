package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationDecisionResponse(
        ChangeVerificationDecisionStatus status,
        int totalRules,
        int satisfied,
        int notSatisfied,
        int notVerified
) {
    public static ChangeVerificationDecisionResponse from(List<ChangeVerificationRuleResultResponse> rules) {
        var sourceRules = rules != null ? rules.stream()
                .filter(rule -> rule != null && rule.scope() != ChangeVerificationRuleScope.ADDITIONAL)
                .toList() : List.<ChangeVerificationRuleResultResponse>of();
        var satisfied = count(sourceRules, ChangeVerificationRuleOutcome.SATISFIED);
        var notSatisfied = count(sourceRules, ChangeVerificationRuleOutcome.NOT_SATISFIED);
        var notVerified = count(sourceRules, ChangeVerificationRuleOutcome.NOT_VERIFIED);
        var status = sourceRules.isEmpty()
                ? ChangeVerificationDecisionStatus.INCONCLUSIVE
                : notSatisfied > 0
                ? ChangeVerificationDecisionStatus.NEEDS_ACTION
                : notVerified > 0
                ? ChangeVerificationDecisionStatus.NEEDS_EVIDENCE
                : ChangeVerificationDecisionStatus.READY;
        return new ChangeVerificationDecisionResponse(
                status,
                sourceRules.size(),
                satisfied,
                notSatisfied,
                notVerified
        );
    }

    private static int count(
            List<ChangeVerificationRuleResultResponse> rules,
            ChangeVerificationRuleOutcome outcome
    ) {
        return (int) rules.stream().filter(rule -> outcome == rule.outcome()).count();
    }
}
