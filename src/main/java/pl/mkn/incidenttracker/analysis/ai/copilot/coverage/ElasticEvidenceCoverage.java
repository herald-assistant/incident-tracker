package pl.mkn.incidenttracker.analysis.ai.copilot.coverage;

public enum ElasticEvidenceCoverage {
    NONE,
    LOGS_PRESENT_NO_EXCEPTION,
    EXCEPTION_PRESENT,
    STACKTRACE_PRESENT,
    TRUNCATED,
    SUFFICIENT
}
