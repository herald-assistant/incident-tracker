package pl.mkn.incidenttracker.analysis.ai.copilot.quality;

import java.util.List;

public record CopilotResponseQualityReport(
        boolean enabled,
        CopilotResponseQualityProperties.Mode mode,
        boolean passed,
        List<CopilotResponseQualityFinding> findings
) {

    public CopilotResponseQualityReport {
        findings = findings != null ? List.copyOf(findings) : List.of();
    }

    public int findingCount() {
        return findings.size();
    }

    public static CopilotResponseQualityReport disabled(CopilotResponseQualityProperties.Mode mode) {
        return new CopilotResponseQualityReport(false, mode, true, List.of());
    }
}
