package pl.mkn.incidenttracker.analysis.mcp.database;

public record ExpectedRelationship(
        String javaField,
        String expectedJoinColumn,
        String expectedTargetTable,
        String expectedTargetColumn
) {
}
