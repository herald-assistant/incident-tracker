package pl.mkn.tdw.features.changeverification.job.api;

public record ChangeVerificationSmokeCleanupResultResponse(
        String strategy,
        String status,
        String action,
        String manualSql,
        String message
) {
}
