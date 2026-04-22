package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbReadonlySqlRequest(
        String sql,
        String reason,
        Integer maxRows
) {
}
