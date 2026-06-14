package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public enum GitLabJavaSpringDataRepositoryStatus {
    DETECTED,
    NOT_SPRING_DATA_REPOSITORY,
    TYPE_NOT_FOUND,
    PARSE_FAILED
}
