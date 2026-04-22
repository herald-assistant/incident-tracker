package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseMetadataClient {

    private final DatabaseConnectionRouter connectionRouter;

    public List<TableMetadata> findTables(
            String environment,
            List<String> schemas,
            String tableNamePattern,
            int limit
    ) {
        if (schemas == null || schemas.isEmpty()) {
            return List.of();
        }

        var sql = new StringBuilder("""
                SELECT *
                FROM (
                    SELECT o.OWNER AS SCHEMA_NAME,
                           o.OBJECT_NAME AS TABLE_NAME,
                           o.OBJECT_TYPE AS TABLE_TYPE,
                           c.COMMENTS AS TABLE_COMMENT
                    FROM (
                        SELECT OWNER, TABLE_NAME AS OBJECT_NAME, 'TABLE' AS OBJECT_TYPE
                        FROM ALL_TABLES
                        WHERE OWNER IN (:owners)
                        UNION ALL
                        SELECT OWNER, VIEW_NAME AS OBJECT_NAME, 'VIEW' AS OBJECT_TYPE
                        FROM ALL_VIEWS
                        WHERE OWNER IN (:owners)
                    ) o
                    LEFT JOIN ALL_TAB_COMMENTS c
                        ON c.OWNER = o.OWNER
                       AND c.TABLE_NAME = o.OBJECT_NAME
                """);
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("owners", schemas);

        if (StringUtils.hasText(tableNamePattern)) {
            sql.append(" WHERE UPPER(o.OBJECT_NAME) LIKE :tableNameLike");
            parameters.put("tableNameLike", "%" + tableNamePattern.trim().toUpperCase(Locale.ROOT) + "%");
        }

        sql.append("""
                    ORDER BY o.OWNER, o.OBJECT_NAME
                )
                WHERE ROWNUM <= :limit
                """);
        parameters.put("limit", limit);

        var handle = connectionRouter.route(environment);
        var rows = handle.namedParameterJdbcTemplate().queryForList(sql.toString(), parameters);
        var tables = rows.stream()
                .map(this::toTableMetadata)
                .toList();

        var columnInfo = loadColumnInfo(environment, tables);
        var primaryKeyColumns = loadPrimaryKeyColumns(environment, tables);
        var importedForeignKeyCounts = loadForeignKeyCounts(environment, tables, "R");
        var exportedForeignKeyCounts = loadExportedForeignKeyCounts(environment, tables);

        return tables.stream()
                .map(table -> table.withDetails(
                        columnInfo.getOrDefault(table.key(), new TableColumnInfo(0, List.of())),
                        primaryKeyColumns.getOrDefault(table.key(), List.of()),
                        importedForeignKeyCounts.getOrDefault(table.key(), 0),
                        exportedForeignKeyCounts.getOrDefault(table.key(), 0)
                ))
                .toList();
    }

    public List<ColumnMetadata> findColumns(
            String environment,
            List<String> schemas,
            String tableNamePattern,
            String columnNamePattern,
            int limit
    ) {
        if (schemas == null || schemas.isEmpty()) {
            return List.of();
        }

        var sql = new StringBuilder("""
                SELECT *
                FROM (
                    SELECT c.OWNER AS SCHEMA_NAME,
                           c.TABLE_NAME,
                           c.COLUMN_NAME,
                           c.DATA_TYPE,
                           c.DATA_LENGTH,
                           c.DATA_PRECISION,
                           c.DATA_SCALE,
                           c.NULLABLE,
                           cc.COMMENTS AS COLUMN_COMMENT
                    FROM ALL_TAB_COLUMNS c
                    LEFT JOIN ALL_COL_COMMENTS cc
                        ON cc.OWNER = c.OWNER
                       AND cc.TABLE_NAME = c.TABLE_NAME
                       AND cc.COLUMN_NAME = c.COLUMN_NAME
                    WHERE c.OWNER IN (:owners)
                """);
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("owners", schemas);

        if (StringUtils.hasText(tableNamePattern)) {
            sql.append(" AND UPPER(c.TABLE_NAME) LIKE :tableNameLike");
            parameters.put("tableNameLike", "%" + tableNamePattern.trim().toUpperCase(Locale.ROOT) + "%");
        }
        if (StringUtils.hasText(columnNamePattern)) {
            sql.append(" AND UPPER(c.COLUMN_NAME) LIKE :columnNameLike");
            parameters.put("columnNameLike", "%" + columnNamePattern.trim().toUpperCase(Locale.ROOT) + "%");
        }

        sql.append("""
                    ORDER BY c.OWNER, c.TABLE_NAME, c.COLUMN_ID
                )
                WHERE ROWNUM <= :limit
                """);
        parameters.put("limit", limit);

        var handle = connectionRouter.route(environment);
        return handle.namedParameterJdbcTemplate().queryForList(sql.toString(), parameters).stream()
                .map(this::toColumnMetadata)
                .toList();
    }

    public TableDescriptionMetadata describeTable(String environment, String schema, String tableName) {
        var normalizedSchema = schema.toUpperCase(Locale.ROOT);
        var normalizedTableName = tableName.toUpperCase(Locale.ROOT);
        var parameters = Map.of(
                "owner", normalizedSchema,
                "tableName", normalizedTableName
        );
        var handle = connectionRouter.route(environment);

        var tableRow = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT OWNER AS SCHEMA_NAME,
                       TABLE_NAME,
                       'TABLE' AS TABLE_TYPE
                FROM ALL_TABLES
                WHERE OWNER = :owner
                  AND TABLE_NAME = :tableName
                UNION ALL
                SELECT OWNER AS SCHEMA_NAME,
                       VIEW_NAME AS TABLE_NAME,
                       'VIEW' AS TABLE_TYPE
                FROM ALL_VIEWS
                WHERE OWNER = :owner
                  AND VIEW_NAME = :tableName
                """, parameters);

        if (tableRow.isEmpty()) {
            throw new IllegalStateException(
                    "Oracle table or view %s.%s does not exist or is not visible to the read-only user."
                            .formatted(normalizedSchema, normalizedTableName)
            );
        }

        var tableType = stringValue(tableRow.get(0).get("TABLE_TYPE"));
        var tableComment = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT COMMENTS
                FROM ALL_TAB_COMMENTS
                WHERE OWNER = :owner
                  AND TABLE_NAME = :tableName
                """, parameters).stream()
                .findFirst()
                .map(row -> stringValue(row.get("COMMENTS")))
                .orElse(null);

        var columns = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT c.COLUMN_NAME,
                       c.DATA_TYPE,
                       c.DATA_LENGTH,
                       c.DATA_PRECISION,
                       c.DATA_SCALE,
                       c.NULLABLE,
                       c.DATA_DEFAULT,
                       cc.COMMENTS
                FROM ALL_TAB_COLUMNS c
                LEFT JOIN ALL_COL_COMMENTS cc
                    ON cc.OWNER = c.OWNER
                   AND cc.TABLE_NAME = c.TABLE_NAME
                   AND cc.COLUMN_NAME = c.COLUMN_NAME
                WHERE c.OWNER = :owner
                  AND c.TABLE_NAME = :tableName
                ORDER BY c.COLUMN_ID
                """, parameters).stream()
                .map(this::toColumnDefinition)
                .toList();

        var primaryKeyColumns = loadPrimaryKeyColumns(environment, List.of(new TableMetadata(
                normalizedSchema,
                normalizedTableName,
                tableType,
                tableComment,
                columns.size(),
                List.of(),
                0,
                0,
                columns.stream().map(ColumnDefinition::name).toList()
        ))).values().stream().findFirst().orElse(List.of());

        var importedForeignKeys = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT ac.CONSTRAINT_NAME,
                       acc.COLUMN_NAME AS SOURCE_COLUMN,
                       r.OWNER AS TARGET_OWNER,
                       r.TABLE_NAME AS TARGET_TABLE,
                       rcc.COLUMN_NAME AS TARGET_COLUMN
                FROM ALL_CONSTRAINTS ac
                JOIN ALL_CONS_COLUMNS acc
                    ON acc.OWNER = ac.OWNER
                   AND acc.CONSTRAINT_NAME = ac.CONSTRAINT_NAME
                JOIN ALL_CONSTRAINTS r
                    ON r.OWNER = ac.R_OWNER
                   AND r.CONSTRAINT_NAME = ac.R_CONSTRAINT_NAME
                JOIN ALL_CONS_COLUMNS rcc
                    ON rcc.OWNER = r.OWNER
                   AND rcc.CONSTRAINT_NAME = r.CONSTRAINT_NAME
                   AND rcc.POSITION = acc.POSITION
                WHERE ac.OWNER = :owner
                  AND ac.TABLE_NAME = :tableName
                  AND ac.CONSTRAINT_TYPE = 'R'
                ORDER BY ac.CONSTRAINT_NAME, acc.POSITION
                """, parameters);

        var exportedForeignKeys = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT child.CONSTRAINT_NAME,
                       childCols.COLUMN_NAME AS SOURCE_COLUMN,
                       child.OWNER AS SOURCE_OWNER,
                       child.TABLE_NAME AS SOURCE_TABLE,
                       parentCols.COLUMN_NAME AS TARGET_COLUMN
                FROM ALL_CONSTRAINTS child
                JOIN ALL_CONSTRAINTS parent
                    ON parent.OWNER = child.R_OWNER
                   AND parent.CONSTRAINT_NAME = child.R_CONSTRAINT_NAME
                JOIN ALL_CONS_COLUMNS childCols
                    ON childCols.OWNER = child.OWNER
                   AND childCols.CONSTRAINT_NAME = child.CONSTRAINT_NAME
                JOIN ALL_CONS_COLUMNS parentCols
                    ON parentCols.OWNER = parent.OWNER
                   AND parentCols.CONSTRAINT_NAME = parent.CONSTRAINT_NAME
                   AND parentCols.POSITION = childCols.POSITION
                WHERE parent.OWNER = :owner
                  AND parent.TABLE_NAME = :tableName
                  AND child.CONSTRAINT_TYPE = 'R'
                ORDER BY child.CONSTRAINT_NAME, childCols.POSITION
                """, parameters);

        var indexes = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT i.INDEX_NAME,
                       i.UNIQUENESS,
                       ic.COLUMN_NAME,
                       ic.COLUMN_POSITION
                FROM ALL_INDEXES i
                JOIN ALL_IND_COLUMNS ic
                    ON ic.INDEX_OWNER = i.OWNER
                   AND ic.INDEX_NAME = i.INDEX_NAME
                WHERE i.TABLE_OWNER = :owner
                  AND i.TABLE_NAME = :tableName
                ORDER BY i.INDEX_NAME, ic.COLUMN_POSITION
                """, parameters);

        var availableTables = loadVisibleTableNames(environment, normalizedSchema);

        return new TableDescriptionMetadata(
                normalizedSchema,
                normalizedTableName,
                tableType,
                tableComment,
                List.copyOf(columns),
                List.copyOf(primaryKeyColumns),
                buildForeignKeys(importedForeignKeys, normalizedSchema, normalizedTableName, true),
                buildForeignKeys(exportedForeignKeys, normalizedSchema, normalizedTableName, false),
                buildIndexes(indexes),
                inferRelationships(normalizedSchema, normalizedTableName, columns, availableTables)
        );
    }

    public boolean tableExists(String environment, String schema, String tableName) {
        try {
            describeTable(environment, schema, tableName);
            return true;
        }
        catch (IllegalStateException exception) {
            return false;
        }
    }

    public boolean columnExists(String environment, String schema, String tableName, String columnName) {
        return describeTable(environment, schema, tableName).columns().stream()
                .anyMatch(column -> column.name().equalsIgnoreCase(columnName));
    }

    private TableMetadata toTableMetadata(Map<String, Object> row) {
        return new TableMetadata(
                stringValue(row.get("SCHEMA_NAME")),
                stringValue(row.get("TABLE_NAME")),
                stringValue(row.get("TABLE_TYPE")),
                stringValue(row.get("TABLE_COMMENT")),
                null,
                List.of(),
                0,
                0,
                List.of()
        );
    }

    private ColumnMetadata toColumnMetadata(Map<String, Object> row) {
        return new ColumnMetadata(
                stringValue(row.get("SCHEMA_NAME")),
                stringValue(row.get("TABLE_NAME")),
                stringValue(row.get("COLUMN_NAME")),
                stringValue(row.get("DATA_TYPE")),
                intValue(row.get("DATA_LENGTH")),
                intValue(row.get("DATA_PRECISION")),
                intValue(row.get("DATA_SCALE")),
                "Y".equalsIgnoreCase(stringValue(row.get("NULLABLE"))),
                stringValue(row.get("COLUMN_COMMENT"))
        );
    }

    private ColumnDefinition toColumnDefinition(Map<String, Object> row) {
        return new ColumnDefinition(
                stringValue(row.get("COLUMN_NAME")),
                stringValue(row.get("DATA_TYPE")),
                intValue(row.get("DATA_LENGTH")),
                intValue(row.get("DATA_PRECISION")),
                intValue(row.get("DATA_SCALE")),
                "Y".equalsIgnoreCase(stringValue(row.get("NULLABLE"))),
                abbreviateDefaultValue(stringValue(row.get("DATA_DEFAULT"))),
                stringValue(row.get("COMMENTS"))
        );
    }

    private Map<TableKey, TableColumnInfo> loadColumnInfo(String environment, List<TableMetadata> tables) {
        if (tables.isEmpty()) {
            return Map.of();
        }

        var handle = connectionRouter.route(environment);
        var owners = tables.stream().map(TableMetadata::schema).distinct().toList();
        var tableNames = tables.stream().map(TableMetadata::tableName).distinct().toList();
        var rows = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT OWNER AS SCHEMA_NAME,
                       TABLE_NAME,
                       COLUMN_NAME
                FROM ALL_TAB_COLUMNS
                WHERE OWNER IN (:owners)
                  AND TABLE_NAME IN (:tableNames)
                ORDER BY OWNER, TABLE_NAME, COLUMN_ID
                """, Map.of(
                "owners", owners,
                "tableNames", tableNames
        ));

        var aggregated = new LinkedHashMap<TableKey, List<String>>();
        for (var row : rows) {
            var key = new TableKey(
                    stringValue(row.get("SCHEMA_NAME")),
                    stringValue(row.get("TABLE_NAME"))
            );
            aggregated.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(stringValue(row.get("COLUMN_NAME")));
        }

        var result = new LinkedHashMap<TableKey, TableColumnInfo>();
        aggregated.forEach((key, columns) -> result.put(key, new TableColumnInfo(columns.size(), List.copyOf(columns))));
        return result;
    }

    private Map<TableKey, List<String>> loadPrimaryKeyColumns(String environment, List<TableMetadata> tables) {
        if (tables.isEmpty()) {
            return Map.of();
        }

        var handle = connectionRouter.route(environment);
        var owners = tables.stream().map(TableMetadata::schema).distinct().toList();
        var tableNames = tables.stream().map(TableMetadata::tableName).distinct().toList();
        var rows = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT ac.OWNER AS SCHEMA_NAME,
                       ac.TABLE_NAME,
                       acc.COLUMN_NAME
                FROM ALL_CONSTRAINTS ac
                JOIN ALL_CONS_COLUMNS acc
                    ON acc.OWNER = ac.OWNER
                   AND acc.CONSTRAINT_NAME = ac.CONSTRAINT_NAME
                WHERE ac.OWNER IN (:owners)
                  AND ac.TABLE_NAME IN (:tableNames)
                  AND ac.CONSTRAINT_TYPE = 'P'
                ORDER BY ac.OWNER, ac.TABLE_NAME, acc.POSITION
                """, Map.of(
                "owners", owners,
                "tableNames", tableNames
        ));

        var result = new LinkedHashMap<TableKey, List<String>>();
        for (var row : rows) {
            var key = new TableKey(
                    stringValue(row.get("SCHEMA_NAME")),
                    stringValue(row.get("TABLE_NAME"))
            );
            result.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(stringValue(row.get("COLUMN_NAME")));
        }
        return result;
    }

    private Map<TableKey, Integer> loadForeignKeyCounts(String environment, List<TableMetadata> tables, String constraintType) {
        if (tables.isEmpty()) {
            return Map.of();
        }

        var handle = connectionRouter.route(environment);
        var owners = tables.stream().map(TableMetadata::schema).distinct().toList();
        var tableNames = tables.stream().map(TableMetadata::tableName).distinct().toList();
        var rows = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT OWNER AS SCHEMA_NAME,
                       TABLE_NAME,
                       COUNT(*) AS FK_COUNT
                FROM ALL_CONSTRAINTS
                WHERE OWNER IN (:owners)
                  AND TABLE_NAME IN (:tableNames)
                  AND CONSTRAINT_TYPE = :constraintType
                GROUP BY OWNER, TABLE_NAME
                """, Map.of(
                "owners", owners,
                "tableNames", tableNames,
                "constraintType", constraintType
        ));

        var result = new LinkedHashMap<TableKey, Integer>();
        for (var row : rows) {
            result.put(
                    new TableKey(stringValue(row.get("SCHEMA_NAME")), stringValue(row.get("TABLE_NAME"))),
                    intValue(row.get("FK_COUNT"))
            );
        }
        return result;
    }

    private Map<TableKey, Integer> loadExportedForeignKeyCounts(String environment, List<TableMetadata> tables) {
        if (tables.isEmpty()) {
            return Map.of();
        }

        var handle = connectionRouter.route(environment);
        var owners = tables.stream().map(TableMetadata::schema).distinct().toList();
        var tableNames = tables.stream().map(TableMetadata::tableName).distinct().toList();
        var rows = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT parent.OWNER AS SCHEMA_NAME,
                       parent.TABLE_NAME,
                       COUNT(*) AS FK_COUNT
                FROM ALL_CONSTRAINTS child
                JOIN ALL_CONSTRAINTS parent
                    ON parent.OWNER = child.R_OWNER
                   AND parent.CONSTRAINT_NAME = child.R_CONSTRAINT_NAME
                WHERE parent.OWNER IN (:owners)
                  AND parent.TABLE_NAME IN (:tableNames)
                  AND child.CONSTRAINT_TYPE = 'R'
                GROUP BY parent.OWNER, parent.TABLE_NAME
                """, Map.of(
                "owners", owners,
                "tableNames", tableNames
        ));

        var result = new LinkedHashMap<TableKey, Integer>();
        for (var row : rows) {
            result.put(
                    new TableKey(stringValue(row.get("SCHEMA_NAME")), stringValue(row.get("TABLE_NAME"))),
                    intValue(row.get("FK_COUNT"))
            );
        }
        return result;
    }

    private List<ForeignKeyMetadata> buildForeignKeys(
            List<Map<String, Object>> rows,
            String schema,
            String tableName,
            boolean imported
    ) {
        var grouped = new LinkedHashMap<String, ForeignKeyBuilder>();
        for (var row : rows) {
            var constraintName = stringValue(row.get("CONSTRAINT_NAME"));
            var builder = grouped.computeIfAbsent(constraintName, ignored -> imported
                    ? new ForeignKeyBuilder(
                    constraintName,
                    new TableKey(schema, tableName),
                    new TableKey(stringValue(row.get("TARGET_OWNER")), stringValue(row.get("TARGET_TABLE")))
            )
                    : new ForeignKeyBuilder(
                    constraintName,
                    new TableKey(stringValue(row.get("SOURCE_OWNER")), stringValue(row.get("SOURCE_TABLE"))),
                    new TableKey(schema, tableName)
            ));
            builder.sourceColumns().add(stringValue(row.get("SOURCE_COLUMN")));
            builder.targetColumns().add(stringValue(row.get("TARGET_COLUMN")));
        }

        return grouped.values().stream()
                .map(ForeignKeyBuilder::build)
                .toList();
    }

    private List<IndexMetadata> buildIndexes(List<Map<String, Object>> rows) {
        var grouped = new LinkedHashMap<String, List<String>>();
        var uniqueIndexNames = new LinkedHashSet<String>();

        for (var row : rows) {
            var indexName = stringValue(row.get("INDEX_NAME"));
            if ("UNIQUE".equalsIgnoreCase(stringValue(row.get("UNIQUENESS")))) {
                uniqueIndexNames.add(indexName);
            }
            grouped.computeIfAbsent(indexName, ignored -> new ArrayList<>())
                    .add(stringValue(row.get("COLUMN_NAME")));
        }

        return grouped.entrySet().stream()
                .map(entry -> new IndexMetadata(
                        entry.getKey(),
                        uniqueIndexNames.contains(entry.getKey()),
                        List.copyOf(entry.getValue())
                ))
                .sorted(Comparator.comparing(IndexMetadata::indexName))
                .toList();
    }

    Set<RelationshipMetadata> inferRelationships(
            String schema,
            String tableName,
            List<ColumnDefinition> columns,
            Set<String> availableTableNames
    ) {
        var relationships = new LinkedHashSet<RelationshipMetadata>();

        for (var column : columns) {
            if (!column.name().endsWith("_ID") || column.name().length() <= 3) {
                continue;
            }

            var baseName = column.name().substring(0, column.name().length() - 3);
            var targetTableName = resolveTargetTableName(baseName, availableTableNames);
            if (targetTableName == null) {
                continue;
            }

            relationships.add(new RelationshipMetadata(
                    new TableKey(schema, tableName),
                    column.name(),
                    new TableKey(schema, targetTableName),
                    "ID",
                    "Inferred from *_ID naming convention",
                    false
            ));
        }

        return relationships;
    }

    private String resolveTargetTableName(String baseName, Set<String> availableTableNames) {
        var candidates = List.of(
                baseName,
                baseName + "S",
                baseName + "ES",
                singularize(baseName),
                pluralize(baseName)
        );

        for (var candidate : candidates) {
            if (candidate != null && availableTableNames.contains(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private String pluralize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.endsWith("Y") && value.length() > 1) {
            return value.substring(0, value.length() - 1) + "IES";
        }
        return value + "S";
    }

    private String singularize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.endsWith("IES") && value.length() > 3) {
            return value.substring(0, value.length() - 3) + "Y";
        }
        if (value.endsWith("S") && value.length() > 1) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Set<String> loadVisibleTableNames(String environment, String schema) {
        var handle = connectionRouter.route(environment);
        var rows = handle.namedParameterJdbcTemplate().queryForList("""
                SELECT TABLE_NAME
                FROM ALL_TABLES
                WHERE OWNER = :owner
                UNION
                SELECT VIEW_NAME AS TABLE_NAME
                FROM ALL_VIEWS
                WHERE OWNER = :owner
                """, Map.of("owner", schema));

        var tableNames = new LinkedHashSet<String>();
        for (var row : rows) {
            tableNames.add(stringValue(row.get("TABLE_NAME")));
        }
        return tableNames;
    }

    String camelToSnakeUpper(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toUpperCase(Locale.ROOT);
    }

    List<String> inferLikelyKeyColumns(TableMetadata tableMetadata) {
        var likely = new ArrayList<String>();

        for (var column : tableMetadata.columnNames()) {
            var normalized = column.toUpperCase(Locale.ROOT);
            if (normalized.equals("ID")
                    || normalized.endsWith("_ID")
                    || normalized.contains("KEY")
                    || normalized.contains("CODE")
                    || normalized.contains("UUID")
                    || normalized.contains("CORRELATION_ID")) {
                likely.add(normalized);
            }
        }

        return List.copyOf(new LinkedHashSet<>(likely));
    }

    private String abbreviateDefaultValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) + "..." : normalized;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}

record TableMetadata(
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
    TableKey key() {
        return new TableKey(schema, tableName);
    }

    TableMetadata withDetails(
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

record ColumnMetadata(
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

record TableDescriptionMetadata(
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

record ColumnDefinition(
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

record ForeignKeyMetadata(
        String constraintName,
        TableKey sourceTable,
        List<String> sourceColumns,
        TableKey targetTable,
        List<String> targetColumns
) {
}

record IndexMetadata(
        String indexName,
        boolean unique,
        List<String> columns
) {
}

record RelationshipMetadata(
        TableKey sourceTable,
        String sourceColumn,
        TableKey targetTable,
        String targetColumn,
        String evidence,
        boolean declared
) {
}

record TableKey(
        String schema,
        String tableName
) {
}

record TableColumnInfo(
        int columnCount,
        List<String> columnNames
) {
}

final class ForeignKeyBuilder {

    private final String constraintName;
    private final TableKey sourceTable;
    private final TableKey targetTable;
    private final List<String> sourceColumns = new ArrayList<>();
    private final List<String> targetColumns = new ArrayList<>();

    ForeignKeyBuilder(String constraintName, TableKey sourceTable, TableKey targetTable) {
        this.constraintName = constraintName;
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
    }

    List<String> sourceColumns() {
        return sourceColumns;
    }

    List<String> targetColumns() {
        return targetColumns;
    }

    ForeignKeyMetadata build() {
        return new ForeignKeyMetadata(
                constraintName,
                sourceTable,
                List.copyOf(sourceColumns),
                targetTable,
                List.copyOf(targetColumns)
        );
    }
}
