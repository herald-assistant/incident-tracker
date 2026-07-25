package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationSmokeTestExecutionResponse(
        String testId,
        String name,
        String status,
        ChangeVerificationSmokeHttpResultResponse http,
        List<ChangeVerificationSmokeAssertionResultResponse> responseAssertions,
        List<ChangeVerificationSmokeAssertionResultResponse> dbAssertions,
        ChangeVerificationSmokeCleanupResultResponse cleanup
) {

    public ChangeVerificationSmokeTestExecutionResponse {
        responseAssertions = responseAssertions != null ? List.copyOf(responseAssertions) : List.of();
        dbAssertions = dbAssertions != null ? List.copyOf(dbAssertions) : List.of();
    }
}
