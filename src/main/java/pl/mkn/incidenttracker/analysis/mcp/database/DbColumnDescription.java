package pl.mkn.incidenttracker.analysis.mcp.database;

public record DbColumnDescription(
        String name,
        String dataType,
        Integer dataLength,
        Integer dataPrecision,
        Integer dataScale,
        boolean nullable,
        String defaultValue,
        String comment
) {
}
