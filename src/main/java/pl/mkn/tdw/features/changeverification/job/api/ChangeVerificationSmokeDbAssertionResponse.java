package pl.mkn.tdw.features.changeverification.job.api;

public record ChangeVerificationSmokeDbAssertionResponse(
        String id,
        String sql,
        String operator,
        String expectedValue,
        String description
) {
}
