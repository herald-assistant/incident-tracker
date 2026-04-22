package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbOrderBy(
        String column,
        SortDirection direction
) {
}
