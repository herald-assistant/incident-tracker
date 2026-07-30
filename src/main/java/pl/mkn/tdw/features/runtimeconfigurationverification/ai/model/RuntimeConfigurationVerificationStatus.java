package pl.mkn.tdw.features.runtimeconfigurationverification.ai.model;

public enum RuntimeConfigurationVerificationStatus {
    NO_BLOCKING_ANOMALIES,
    REVIEW_REQUIRED,
    LIKELY_CONFIGURATION_ERROR,
    INCOMPLETE
}
