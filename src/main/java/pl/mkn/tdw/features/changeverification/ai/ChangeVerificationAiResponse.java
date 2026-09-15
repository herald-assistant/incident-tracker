package pl.mkn.tdw.features.changeverification.ai;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVisibilityLimitResponse;

import java.util.List;

public record ChangeVerificationAiResponse(
        List<ChangeVerificationRuleResultResponse> rules,
        List<ChangeVerificationRuleResultResponse> additionalChecks,
        List<ChangeVerificationVisibilityLimitResponse> visibilityLimits
) {
    private static final int MAX_ADDITIONAL_CHECKS = 5;

    public ChangeVerificationAiResponse {
        rules = rules != null ? List.copyOf(rules) : List.of();
        additionalChecks = additionalChecks != null
                ? List.copyOf(additionalChecks.stream().limit(MAX_ADDITIONAL_CHECKS).toList())
                : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
