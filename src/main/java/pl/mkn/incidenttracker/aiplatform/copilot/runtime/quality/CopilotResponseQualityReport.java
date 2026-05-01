package pl.mkn.incidenttracker.aiplatform.copilot.runtime.quality;

import java.util.List;

public record CopilotResponseQualityReport(
        boolean enabled,
        CopilotResponseQualityMode mode,
        boolean passed,
        List<Finding> findings
) {

    public CopilotResponseQualityReport {
        findings = findings != null ? List.copyOf(findings) : List.of();
    }

    public int findingCount() {
        return findings.size();
    }

    public static CopilotResponseQualityReport disabled(CopilotResponseQualityMode mode) {
        return new CopilotResponseQualityReport(false, mode, true, List.of());
    }

    public record Finding(
            CopilotResponseQualitySeverity severity,
            String code,
            String field,
            String message
    ) {
    }
}
