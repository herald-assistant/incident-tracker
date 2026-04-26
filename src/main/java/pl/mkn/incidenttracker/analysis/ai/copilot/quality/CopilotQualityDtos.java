package pl.mkn.incidenttracker.analysis.ai.copilot.quality;

import java.util.List;

public final class CopilotQualityDtos {

    private CopilotQualityDtos() {
    }

    public record Finding(
            CopilotResponseQualitySeverity severity,
            String code,
            String field,
            String message
    ) {
    }

    public record Report(
            boolean enabled,
            CopilotResponseQualityProperties.Mode mode,
            boolean passed,
            List<Finding> findings
    ) {

        public Report {
            findings = findings != null ? List.copyOf(findings) : List.of();
        }

        public int findingCount() {
            return findings.size();
        }

        public static Report disabled(CopilotResponseQualityProperties.Mode mode) {
            return new Report(false, mode, true, List.of());
        }
    }
}
