package pl.mkn.tdw.features.changeverification.job.api;

public record ChangeVerificationSmokeAssertionResultResponse(
        String type,
        String target,
        String status,
        String message
) {
}
