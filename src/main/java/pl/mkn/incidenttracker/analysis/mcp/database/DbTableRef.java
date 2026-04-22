package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbTableRef(
        String schema,
        String tableName
) {
}
