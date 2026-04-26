package pl.mkn.incidenttracker.analysis.ai.copilot.quality;

public record CopilotResponseQualityFinding(
        CopilotResponseQualitySeverity severity,
        String code,
        String field,
        String message
) {
}
