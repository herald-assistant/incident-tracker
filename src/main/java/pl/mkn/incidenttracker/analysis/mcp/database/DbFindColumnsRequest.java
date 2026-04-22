package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbFindColumnsRequest(
        String applicationNamePattern,
        String tableNamePattern,
        String columnNamePattern,
        String javaFieldNameHint,
        Integer limit
) {
}
