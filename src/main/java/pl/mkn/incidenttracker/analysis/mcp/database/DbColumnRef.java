package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbColumnRef(
        DbTableRef table,
        String column
) {
}
