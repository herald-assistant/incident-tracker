package pl.mkn.incidenttracker.analysis.mcp.database;

public record ExpectedColumn(
        String javaField,
        String expectedColumn,
        String expectedSqlType,
        Boolean nullable,
        Boolean id
) {
}
