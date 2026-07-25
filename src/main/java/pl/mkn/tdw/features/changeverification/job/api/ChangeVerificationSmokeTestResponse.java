package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationSmokeTestResponse(
        String id,
        String name,
        String method,
        String path,
        String purpose,
        List<ChangeVerificationNameValueResponse> headers,
        List<ChangeVerificationNameValueResponse> queryParams,
        String requestBody,
        List<ChangeVerificationSmokeAssertionResponse> responseAssertions,
        List<String> dbAssertions,
        List<ChangeVerificationSmokeDbAssertionResponse> dbAssertionSpecs,
        ChangeVerificationSmokeCleanupResponse cleanup,
        List<String> cleanupHints,
        List<String> sourceRefs,
        String riskCovered,
        String reviewStatus
) {

    public ChangeVerificationSmokeTestResponse {
        headers = headers != null ? List.copyOf(headers) : List.of();
        queryParams = queryParams != null ? List.copyOf(queryParams) : List.of();
        responseAssertions = responseAssertions != null ? List.copyOf(responseAssertions) : List.of();
        dbAssertions = dbAssertions != null ? List.copyOf(dbAssertions) : List.of();
        dbAssertionSpecs = dbAssertionSpecs != null ? List.copyOf(dbAssertionSpecs) : List.of();
        cleanupHints = cleanupHints != null ? List.copyOf(cleanupHints) : List.of();
        sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
    }
}
