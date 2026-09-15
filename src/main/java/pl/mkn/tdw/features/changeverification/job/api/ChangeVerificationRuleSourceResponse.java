package pl.mkn.tdw.features.changeverification.job.api;

public record ChangeVerificationRuleSourceResponse(
        ChangeVerificationRuleSourceType type,
        String label,
        String reference,
        String quote
) {
}
