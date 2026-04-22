package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbTableDescription(
        String environment,
        String databaseAlias,
        String schema,
        String tableName,
        String tableType,
        String comment,
        List<DbColumnDescription> columns,
        List<String> primaryKeyColumns,
        List<DbForeignKeyDescription> importedForeignKeys,
        List<DbForeignKeyDescription> exportedForeignKeys,
        List<DbIndexDescription> indexes,
        List<DbRelationshipHint> inferredRelationships,
        List<String> warnings
) {
}
