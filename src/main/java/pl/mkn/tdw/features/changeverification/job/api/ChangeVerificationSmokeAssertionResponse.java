package pl.mkn.tdw.features.changeverification.job.api;

public record ChangeVerificationSmokeAssertionResponse(
        String type,
        String target,
        String operator,
        String expectedValue
) {
}
