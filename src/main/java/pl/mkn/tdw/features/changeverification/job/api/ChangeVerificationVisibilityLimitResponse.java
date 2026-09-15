package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationVisibilityLimitResponse(
        String message,
        List<String> affectedRuleIds
) {
    public ChangeVerificationVisibilityLimitResponse {
        affectedRuleIds = affectedRuleIds != null ? List.copyOf(affectedRuleIds) : List.of();
    }
}
