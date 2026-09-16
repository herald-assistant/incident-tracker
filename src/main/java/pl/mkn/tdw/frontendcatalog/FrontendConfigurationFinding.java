package pl.mkn.tdw.frontendcatalog;

public record FrontendConfigurationFinding(
        String severity,
        String code,
        String message,
        String entityType,
        String entityId
) {
}

