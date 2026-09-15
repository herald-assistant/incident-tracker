package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationRuleResultResponse(
        String id,
        ChangeVerificationRuleScope scope,
        ChangeVerificationRuleSourceResponse source,
        String normalizedRule,
        ChangeVerificationInterpretationType interpretationType,
        ChangeVerificationRuleOutcome outcome,
        ChangeVerificationReleaseImpact releaseImpact,
        String conclusion,
        List<ChangeVerificationRuleEvidenceResponse> evidence,
        List<String> missingEvidence,
        String action,
        String rationale,
        String riskIfOmitted,
        List<String> signals,
        String confidence
) {
    public ChangeVerificationRuleResultResponse {
        evidence = evidence != null ? List.copyOf(evidence) : List.of();
        missingEvidence = missingEvidence != null ? List.copyOf(missingEvidence) : List.of();
        signals = signals != null ? List.copyOf(signals) : List.of();
    }
}
