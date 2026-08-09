package pl.mkn.tdw.features.configdriftviewer.ai.model;

public enum ConfigDriftViewerStatus {
    NO_BLOCKING_ANOMALIES,
    REVIEW_REQUIRED,
    LIKELY_CONFIGURATION_ERROR,
    INCOMPLETE
}
