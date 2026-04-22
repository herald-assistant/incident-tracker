package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbTableCandidate(
        String schema,
        String tableName,
        String tableType,
        String comment,
        Integer columnCount,
        List<String> primaryKeyColumns,
        List<String> likelyKeyColumns,
        Integer importedForeignKeyCount,
        Integer exportedForeignKeyCount,
        List<String> matchedBecause
) {
}
