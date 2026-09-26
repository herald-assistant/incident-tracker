package pl.mkn.tdw.integrations.database.internal.metadata;

import java.util.List;
import java.util.Set;

public final class DatabaseMetadataModels {

    private DatabaseMetadataModels() {
    }

    public record TableMetadata(
            String schema,
            String tableName,
            String tableType,
            String comment,
            Integer columnCount,
            List<String> primaryKeyColumns,
            Integer importedForeignKeyCount,
            Integer exportedForeignKeyCount,
            List<String> columnNames
    ) {
        public TableKey key() {
            return new TableKey(schema, tableName);
        }

        public TableMetadata withDetails(
                TableColumnInfo tableColumnInfo,
                List<String> primaryKeys,
                Integer importedCount,
                Integer exportedCount
        ) {
            return new TableMetadata(
                    schema,
                    tableName,
                    tableType,
                    comment,
                    tableColumnInfo.columnCount(),
                    List.copyOf(primaryKeys),
                    importedCount,
                    exportedCount,
                    tableColumnInfo.columnNames()
            );
        }
    }

    public record ColumnMetadata(
            String schema,
            String tableName,
            String columnName,
            String dataType,
            Integer dataLength,
            Integer dataPrecision,
            Integer dataScale,
            boolean nullable,
            String comment
    ) {
    }

    public record TableDescriptionMetadata(
            String schema,
            String tableName,
            String tableType,
            String comment,
            List<ColumnDefinition> columns,
            List<String> primaryKeyColumns,
            List<ForeignKeyMetadata> importedForeignKeys,
            List<ForeignKeyMetadata> exportedForeignKeys,
            List<IndexMetadata> indexes,
            Set<RelationshipMetadata> inferredRelationships
    ) {
    }

    public record ColumnDefinition(
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

    public record ForeignKeyMetadata(
            String constraintName,
            TableKey sourceTable,
            List<String> sourceColumns,
            TableKey targetTable,
            List<String> targetColumns
    ) {
    }

    public record IndexMetadata(
            String indexName,
            boolean unique,
            List<String> columns
    ) {
    }

    public record RelationshipMetadata(
            TableKey sourceTable,
            String sourceColumn,
            TableKey targetTable,
            String targetColumn,
            String evidence,
            boolean declared
    ) {
    }

    public record TableKey(
            String schema,
            String tableName
    ) {
    }

    public record TableColumnInfo(
            int columnCount,
            List<String> columnNames
    ) {
    }
}
