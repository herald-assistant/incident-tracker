package pl.mkn.tdw.features.uxinspector.api;

import java.util.List;

public record UxInspectorInputOptionsResponse(
        String featureId,
        List<SystemOption> systems,
        List<ConfigurationFinding> configurationFindings
) {
    public UxInspectorInputOptionsResponse {
        systems = systems != null ? List.copyOf(systems) : List.of();
        configurationFindings = configurationFindings != null ? List.copyOf(configurationFindings) : List.of();
    }
    public record SystemOption(String systemId, String label, String summary, String defaultBranch) {}
    public record ConfigurationFinding(String severity, String code, String message, String entityType, String entityId) {}
}

