package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbForeignKeyDescription(
        String constraintName,
        DbTableRef sourceTable,
        List<String> sourceColumns,
        DbTableRef targetTable,
        List<String> targetColumns
) {
}
