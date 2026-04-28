package pl.mkn.incidenttracker.analysis.mcp.database;

public final class DatabaseToolNames {

    public static final String PREFIX = "db_";

    public static final String GET_SCOPE = PREFIX + "get_scope";
    public static final String FIND_TABLES = PREFIX + "find_tables";
    public static final String FIND_COLUMNS = PREFIX + "find_columns";
    public static final String DESCRIBE_TABLE = PREFIX + "describe_table";
    public static final String EXISTS_BY_KEY = PREFIX + "exists_by_key";
    public static final String COUNT_ROWS = PREFIX + "count_rows";
    public static final String GROUP_COUNT = PREFIX + "group_count";
    public static final String SAMPLE_ROWS = PREFIX + "sample_rows";
    public static final String CHECK_ORPHANS = PREFIX + "check_orphans";
    public static final String FIND_RELATIONSHIPS = PREFIX + "find_relationships";
    public static final String JOIN_COUNT = PREFIX + "join_count";
    public static final String JOIN_SAMPLE = PREFIX + "join_sample";
    public static final String COMPARE_TABLE_TO_EXPECTED_MAPPING = PREFIX + "compare_table_to_expected_mapping";
    public static final String EXECUTE_READONLY_SQL = PREFIX + "execute_readonly_sql";

    private DatabaseToolNames() {
    }
}
