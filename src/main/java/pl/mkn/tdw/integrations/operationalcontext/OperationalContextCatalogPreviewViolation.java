package pl.mkn.tdw.integrations.operationalcontext;

public record OperationalContextCatalogPreviewViolation(
        String code,
        String fingerprint,
        String ruleCode,
        String severity
) {
}
