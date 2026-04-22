package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbFindTablesRequest(
        String applicationNamePattern,
        String tableNamePattern,
        String entityOrKeywordHint,
        Integer limit
) {
}
