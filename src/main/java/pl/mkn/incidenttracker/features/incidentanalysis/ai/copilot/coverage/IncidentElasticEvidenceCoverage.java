package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage;

public enum IncidentElasticEvidenceCoverage {
    NONE,
    LOGS_PRESENT_NO_EXCEPTION,
    EXCEPTION_PRESENT,
    STACKTRACE_PRESENT,
    TRUNCATED,
    SUFFICIENT
}
