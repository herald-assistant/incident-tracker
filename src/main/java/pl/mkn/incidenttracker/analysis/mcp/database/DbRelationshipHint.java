package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbRelationshipHint(
        DbTableRef sourceTable,
        String sourceColumn,
        DbTableRef targetTable,
        String targetColumn,
        String evidence,
        boolean declared
) {
}
