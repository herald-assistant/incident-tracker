package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationSmokePackResponse(
        boolean requested,
        String status,
        String postmanCollectionName,
        List<ChangeVerificationSmokeTestResponse> tests,
        List<String> visibilityLimits,
        List<String> suggestedActions,
        String confidence
) {

    public ChangeVerificationSmokePackResponse {
        tests = tests != null ? List.copyOf(tests) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        suggestedActions = suggestedActions != null ? List.copyOf(suggestedActions) : List.of();
    }
}
