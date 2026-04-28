package pl.mkn.incidenttracker.analysis.mcp.gitlab;

public final class GitLabToolNames {

    public static final String PREFIX = "gitlab_";

    public static final String SEARCH_REPOSITORY_CANDIDATES = PREFIX + "search_repository_candidates";
    public static final String READ_REPOSITORY_FILE = PREFIX + "read_repository_file";
    public static final String READ_REPOSITORY_FILE_CHUNK = PREFIX + "read_repository_file_chunk";
    public static final String READ_REPOSITORY_FILE_CHUNKS = PREFIX + "read_repository_file_chunks";
    public static final String READ_REPOSITORY_FILE_OUTLINE = PREFIX + "read_repository_file_outline";
    public static final String FIND_CLASS_REFERENCES = PREFIX + "find_class_references";
    public static final String FIND_FLOW_CONTEXT = PREFIX + "find_flow_context";

    private GitLabToolNames() {
    }
}
