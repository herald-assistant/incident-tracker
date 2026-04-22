package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbJoinCondition(
        DbColumnRef left,
        DbColumnRef right,
        JoinType type
) {
}
