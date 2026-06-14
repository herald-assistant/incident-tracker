package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public enum GitLabJavaExternalTypeClassification {
    SKIP_SOURCE_LOOKUP(false),
    SEMANTIC_SIGNAL(false),
    TERMINAL_BOUNDARY(false),
    LOCAL_LOOKUP_FIRST(true);

    private final boolean sourceLookupAllowed;

    GitLabJavaExternalTypeClassification(boolean sourceLookupAllowed) {
        this.sourceLookupAllowed = sourceLookupAllowed;
    }

    public boolean sourceLookupAllowed() {
        return sourceLookupAllowed;
    }
}
