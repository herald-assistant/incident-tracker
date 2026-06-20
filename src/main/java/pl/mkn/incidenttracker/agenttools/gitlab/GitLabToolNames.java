package pl.mkn.incidenttracker.agenttools.gitlab;

public final class GitLabToolNames {

    public static final String PREFIX = "gitlab_";

    public static final String LIST_AVAILABLE_REPOSITORIES = PREFIX + "list_available_repositories";
    public static final String LIST_REPOSITORY_ENDPOINTS = PREFIX + "list_repository_endpoints";
    public static final String BUILD_ENDPOINT_USE_CASE_CONTEXT = PREFIX + "build_endpoint_use_case_context";
    public static final String SEARCH_REPOSITORY_CANDIDATES = PREFIX + "search_repository_candidates";
    public static final String READ_REPOSITORY_FILE = PREFIX + "read_repository_file";
    public static final String READ_REPOSITORY_FILES_BY_PATH = PREFIX + "read_repository_files_by_path";
    public static final String READ_REPOSITORY_FILE_CHUNK = PREFIX + "read_repository_file_chunk";
    public static final String READ_REPOSITORY_FILE_CHUNKS = PREFIX + "read_repository_file_chunks";
    public static final String READ_REPOSITORY_FILE_OUTLINE = PREFIX + "read_repository_file_outline";
    public static final String READ_JAVA_METHOD_SLICE = PREFIX + "read_java_method_slice";
    public static final String FIND_CLASS_REFERENCES = PREFIX + "find_class_references";
    public static final String FIND_FLOW_CONTEXT = PREFIX + "find_flow_context";

    private GitLabToolNames() {
    }
}
