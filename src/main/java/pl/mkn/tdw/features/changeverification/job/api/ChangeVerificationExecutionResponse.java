package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationExecutionResponse(
        boolean requested,
        String status,
        List<String> executedTestIds,
        List<ChangeVerificationSmokeTestExecutionResponse> testResults,
        List<String> cleanupActions,
        String manualCleanupSql,
        List<String> visibilityLimits
) {

    public ChangeVerificationExecutionResponse {
        executedTestIds = executedTestIds != null ? List.copyOf(executedTestIds) : List.of();
        testResults = testResults != null ? List.copyOf(testResults) : List.of();
        cleanupActions = cleanupActions != null ? List.copyOf(cleanupActions) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
