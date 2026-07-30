package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

public enum RuntimeConfigurationFileStatus {
    AVAILABLE,
    MISSING,
    AMBIGUOUS,
    TRUNCATED,
    ERROR,
    BRANCH_MISSING
}
