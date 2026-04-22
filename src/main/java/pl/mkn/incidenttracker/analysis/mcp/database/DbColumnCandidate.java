package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbColumnCandidate(
        String schema,
        String tableName,
        String columnName,
        String dataType,
        Integer dataLength,
        Integer dataPrecision,
        Integer dataScale,
        boolean nullable,
        String comment,
        List<String> matchedBecause
) {
}
