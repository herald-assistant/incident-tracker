package pl.mkn.incidenttracker.integrations.gitlab.usecase;

final class GitLabEndpointUseCaseWarningCodes {

    static final String BRANCH_REF_NOT_IMMUTABLE = "BRANCH_REF_NOT_IMMUTABLE";
    static final String SOURCE_SCOPE_MISSING = "SOURCE_SCOPE_MISSING";
    static final String SOURCE_TREE_UNAVAILABLE = "SOURCE_TREE_UNAVAILABLE";
    static final String SOURCE_FILE_LIMIT_REACHED = "SOURCE_FILE_LIMIT_REACHED";
    static final String SOURCE_FILE_TRUNCATED = "SOURCE_FILE_TRUNCATED";
    static final String SOURCE_FILE_READ_FAILED = "SOURCE_FILE_READ_FAILED";
    static final String SOURCE_PARSE_FAILED = "SOURCE_PARSE_FAILED";
    static final String ENDPOINT_MATCH_INPUT_MISSING = "ENDPOINT_MATCH_INPUT_MISSING";
    static final String ENDPOINT_NOT_FOUND = "ENDPOINT_NOT_FOUND";
    static final String ENDPOINT_AMBIGUOUS = "ENDPOINT_AMBIGUOUS";
    static final String ENDPOINT_PATH_UNRESOLVED = "ENDPOINT_PATH_UNRESOLVED";
    static final String REPOSITORY_ENDPOINT_INVENTORY_FALLBACK_FAILED = "REPOSITORY_ENDPOINT_INVENTORY_FALLBACK_FAILED";
    static final String BEAN_ASSIGNABLE_TYPE_UNRESOLVED = "BEAN_ASSIGNABLE_TYPE_UNRESOLVED";
    static final String DI_BEAN_NOT_FOUND = "DI_BEAN_NOT_FOUND";
    static final String DI_BEAN_AMBIGUOUS = "DI_BEAN_AMBIGUOUS";
    static final String DI_QUALIFIER_NOT_FOUND = "DI_QUALIFIER_NOT_FOUND";
    static final String CALL_TARGET_UNRESOLVED = "CALL_TARGET_UNRESOLVED";
    static final String CALL_TARGET_AMBIGUOUS = "CALL_TARGET_AMBIGUOUS";
    static final String MAX_DEPTH_REACHED = "MAX_DEPTH_REACHED";
    static final String MAX_NODES_REACHED = "MAX_NODES_REACHED";
    static final String CALL_GRAPH_CYCLE_DETECTED = "CALL_GRAPH_CYCLE_DETECTED";

    private GitLabEndpointUseCaseWarningCodes() {
    }
}
