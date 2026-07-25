package pl.mkn.tdw.features.changeverification.job.api;

public record ChangeVerificationNameValueResponse(
        String name,
        String value,
        boolean enabled
) {
}
