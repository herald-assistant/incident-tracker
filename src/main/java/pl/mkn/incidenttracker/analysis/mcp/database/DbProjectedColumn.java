package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbProjectedColumn(
        DbColumnRef column,
        String alias
) {
}
