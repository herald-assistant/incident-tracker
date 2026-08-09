package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationVerificationCheckResponse(
        String id,
        String origin,
        String scope,
        String criterionSource,
        String criterionQuote,
        String interpretationType,
        String criticality,
        String inferenceRationale,
        List<String> inferenceSignals,
        String riskIfOmitted,
        String confidence,
        String expectedCriterion,
        String verificationStatus,
        String verifiedAgainst,
        String analysis,
        List<String> evidenceRefs,
        List<String> gaps,
        String suggestedAction
) {

    public ChangeVerificationVerificationCheckResponse {
        inferenceSignals = inferenceSignals != null ? List.copyOf(inferenceSignals) : List.of();
        evidenceRefs = evidenceRefs != null ? List.copyOf(evidenceRefs) : List.of();
        gaps = gaps != null ? List.copyOf(gaps) : List.of();
    }
}
