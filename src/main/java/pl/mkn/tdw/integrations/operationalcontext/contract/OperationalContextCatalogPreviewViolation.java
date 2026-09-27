package pl.mkn.tdw.integrations.operationalcontext.contract;

public record OperationalContextCatalogPreviewViolation(
        String code,
        String fingerprint,
        String ruleCode,
        String severity,
        String message,
        String entityType,
        String entityId,
        String fieldPath
) {
}
