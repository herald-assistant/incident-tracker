package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.mcp.database.DbApplicationScopeInfo;
import pl.mkn.incidenttracker.analysis.mcp.database.DbCheckOrphansRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbColumnCandidate;
import pl.mkn.incidenttracker.analysis.mcp.database.DbColumnDescription;
import pl.mkn.incidenttracker.analysis.mcp.database.DbColumnRef;
import pl.mkn.incidenttracker.analysis.mcp.database.DbColumnSearchResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbCountResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbCountRowsRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbDescribeTableRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbExistsByKeyRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbExistsResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbFilter;
import pl.mkn.incidenttracker.analysis.mcp.database.DbFindColumnsRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbFindRelationshipsRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbFindTablesRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbForeignKeyDescription;
import pl.mkn.incidenttracker.analysis.mcp.database.DbGroupCountRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbGroupCountResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbIndexDescription;
import pl.mkn.incidenttracker.analysis.mcp.database.DbJoinCondition;
import pl.mkn.incidenttracker.analysis.mcp.database.DbJoinCountRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbJoinCountResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbJoinSampleRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbJoinSampleResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbKeyValue;
import pl.mkn.incidenttracker.analysis.mcp.database.DbMappingComparisonRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbMappingComparisonResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbOperator;
import pl.mkn.incidenttracker.analysis.mcp.database.DbOrderBy;
import pl.mkn.incidenttracker.analysis.mcp.database.DbOrphanCheckResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbProjectedColumn;
import pl.mkn.incidenttracker.analysis.mcp.database.DbQualifiedFilter;
import pl.mkn.incidenttracker.analysis.mcp.database.DbReadonlySqlRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbReadonlySqlResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbRelationshipHint;
import pl.mkn.incidenttracker.analysis.mcp.database.DbRelationshipsResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbSampleRowsRequest;
import pl.mkn.incidenttracker.analysis.mcp.database.DbSampleRowsResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbScopeResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbTableCandidate;
import pl.mkn.incidenttracker.analysis.mcp.database.DbTableDescription;
import pl.mkn.incidenttracker.analysis.mcp.database.DbTableRef;
import pl.mkn.incidenttracker.analysis.mcp.database.DbTableSearchResult;
import pl.mkn.incidenttracker.analysis.mcp.database.DbToolScope;
import pl.mkn.incidenttracker.analysis.mcp.database.ExpectedColumn;
import pl.mkn.incidenttracker.analysis.mcp.database.ExpectedRelationship;
import pl.mkn.incidenttracker.analysis.mcp.database.JoinType;
import pl.mkn.incidenttracker.analysis.mcp.database.SortDirection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseToolService {

    private final DatabaseToolProperties properties;
    private final DatabaseApplicationScopeResolver scopeResolver;
    private final DatabaseMetadataClient metadataClient;
    private final DatabaseReadOnlyQueryClient queryClient;
    private final DatabaseSqlGuard sqlGuard;
    private final DatabaseResultMasker resultMasker;
    private final DatabaseResultLimiter resultLimiter;

    public DbScopeResult getScope(DbToolScope scope) {
        var environment = scopeResolver.requiredEnvironment(scope.environment());
        var warnings = new ArrayList<String>();
        if (properties.isAllowAllSchemas()) {
            warnings.add("Configured schema scope is broad because allowAllSchemas=true.");
        }

        return new DbScopeResult(
                scope.environment(),
                environment.getDatabaseAlias(),
                environment.getDescription(),
                scopeResolver.describeApplications(scope.environment()),
                scopeResolver.allowedSchemas(environment),
                List.of(
                        "Environment is taken from hidden session context.",
                        "Discovery is application-scoped and prefers configured application-to-schema mappings.",
                        "Exact data reads require explicit schema.table references returned by prior discovery.",
                        "All filter values are executed as bind parameters.",
                        "Raw SQL is disabled by default and typed tools are preferred."
                ),
                List.copyOf(warnings)
        );
    }

    public DbTableSearchResult findTables(DbToolScope scope, DbFindTablesRequest request) {
        var resolvedScope = scopeResolver.resolveDiscoveryScope(scope.environment(), request.applicationNamePattern());
        var effectiveLimit = normalizeDiscoveryLimit(request.limit(), properties.getMaxTablesPerSearch());
        var fetchLimit = effectiveLimit + 1;
        var tables = metadataClient.findTables(
                scope.environment(),
                resolvedScope.resolvedSchemas(),
                request.tableNamePattern(),
                fetchLimit
        );
        var truncated = tables.size() > effectiveLimit;
        var candidates = tables.stream()
                .map(table -> rankTableCandidate(table, request, resolvedScope))
                .sorted(Comparator.comparingInt(ScoredTableCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.candidate().schema())
                        .thenComparing(candidate -> candidate.candidate().tableName()))
                .limit(effectiveLimit)
                .map(ScoredTableCandidate::candidate)
                .toList();

        return new DbTableSearchResult(
                scope.environment(),
                resolvedScope.databaseAlias(),
                resolvedScope.applicationAlias(),
                resolvedScope.resolvedSchemas(),
                candidates,
                truncated,
                mergeWarnings(resolvedScope.warnings())
        );
    }

    public DbColumnSearchResult findColumns(DbToolScope scope, DbFindColumnsRequest request) {
        var resolvedScope = scopeResolver.resolveDiscoveryScope(scope.environment(), request.applicationNamePattern());
        var effectiveLimit = normalizeDiscoveryLimit(request.limit(), properties.getMaxColumnsPerSearch());
        var fetchLimit = effectiveLimit + 1;
        var requestedColumnPattern = firstNonBlank(
                request.columnNamePattern(),
                metadataClient.camelToSnakeUpper(request.javaFieldNameHint())
        );
        var columns = metadataClient.findColumns(
                scope.environment(),
                resolvedScope.resolvedSchemas(),
                request.tableNamePattern(),
                requestedColumnPattern,
                fetchLimit
        );
        var truncated = columns.size() > effectiveLimit;
        var candidates = columns.stream()
                .map(column -> rankColumnCandidate(column, request, resolvedScope))
                .sorted(Comparator.comparingInt(ScoredColumnCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.candidate().schema())
                        .thenComparing(candidate -> candidate.candidate().tableName())
                        .thenComparing(candidate -> candidate.candidate().columnName()))
                .limit(effectiveLimit)
                .map(ScoredColumnCandidate::candidate)
                .toList();

        return new DbColumnSearchResult(
                scope.environment(),
                resolvedScope.databaseAlias(),
                resolvedScope.applicationAlias(),
                resolvedScope.resolvedSchemas(),
                candidates,
                truncated,
                mergeWarnings(resolvedScope.warnings())
        );
    }

    public DbTableDescription describeTable(DbToolScope scope, DbDescribeTableRequest request) {
        var table = sqlGuard.normalizeTableRef(scope, request.table());
        var description = metadataClient.describeTable(scope.environment(), table.schema(), table.tableName());

        return new DbTableDescription(
                scope.environment(),
                databaseAlias(scope),
                description.schema(),
                description.tableName(),
                description.tableType(),
                description.comment(),
                description.columns().stream()
                        .map(column -> new DbColumnDescription(
                                column.name(),
                                column.dataType(),
                                column.dataLength(),
                                column.dataPrecision(),
                                column.dataScale(),
                                column.nullable(),
                                column.defaultValue(),
                                column.comment()
                        ))
                        .toList(),
                description.primaryKeyColumns(),
                toForeignKeyDescriptions(description.importedForeignKeys()),
                toForeignKeyDescriptions(description.exportedForeignKeys()),
                description.indexes().stream()
                        .map(index -> new DbIndexDescription(index.indexName(), index.unique(), index.columns()))
                        .toList(),
                toRelationshipHints(description.inferredRelationships()),
                List.of()
        );
    }

    public DbExistsResult existsByKey(DbToolScope scope, DbExistsByKeyRequest request) {
        var table = sqlGuard.normalizeTableRef(scope, request.table());
        var keyValues = safeList(request.keyValues());
        if (keyValues.isEmpty()) {
            throw new IllegalArgumentException("db_exists_by_key requires at least one key column/value.");
        }

        var where = buildKeyWhereClause(scope, table, "t", keyValues);
        var count = queryClient.queryForCount(
                scope,
                "SELECT COUNT(*) FROM %s.%s t%s".formatted(
                        table.schema(),
                        table.tableName(),
                        where.sql()
                ),
                where.parameters()
        );

        var projectionRows = List.<Map<String, Object>>of();
        if (count > 0 && !safeList(request.projectionColumns()).isEmpty()) {
            var projectionColumns = sqlGuard.validateColumns(scope, table, request.projectionColumns());
            var rows = queryClient.queryForRows(
                    scope,
                    "SELECT %s FROM %s.%s t%s FETCH FIRST 5 ROWS ONLY".formatted(
                            projectionColumns.stream().map(column -> "t." + column).collect(java.util.stream.Collectors.joining(", ")),
                            table.schema(),
                            table.tableName(),
                            where.sql()
                    ),
                    where.parameters()
            );
            projectionRows = limitAndMask(rows, 5).rows();
        }

        return new DbExistsResult(
                scope.environment(),
                databaseAlias(scope),
                table,
                count > 0,
                count,
                projectionRows,
                List.of()
        );
    }

    public DbCountResult countRows(DbToolScope scope, DbCountRowsRequest request) {
        var table = sqlGuard.normalizeTableRef(scope, request.table());
        var where = buildWhereClause(scope, table, "t", request.filters());
        var count = queryClient.queryForCount(
                scope,
                "SELECT COUNT(*) FROM %s.%s t%s".formatted(
                        table.schema(),
                        table.tableName(),
                        where.sql()
                ),
                where.parameters()
        );

        return new DbCountResult(
                scope.environment(),
                databaseAlias(scope),
                table,
                count,
                where.appliedFilters(),
                List.of()
        );
    }

    public DbGroupCountResult groupCount(DbToolScope scope, DbGroupCountRequest request) {
        var table = sqlGuard.normalizeTableRef(scope, request.table());
        var groupByColumns = sqlGuard.validateColumns(scope, table, request.groupByColumns());
        if (groupByColumns.isEmpty()) {
            throw new IllegalArgumentException("db_group_count requires at least one groupBy column.");
        }

        var where = buildWhereClause(scope, table, "t", request.filters());
        var effectiveLimit = normalizePositive(request.limit(), properties.getMaxRows());
        var selectColumns = groupByColumns.stream()
                .map(column -> "t." + column)
                .collect(java.util.stream.Collectors.joining(", "));
        var sql = """
                SELECT %s, COUNT(*) AS ROW_COUNT
                FROM %s.%s t%s
                GROUP BY %s
                ORDER BY ROW_COUNT DESC
                FETCH FIRST %d ROWS ONLY
                """.formatted(
                selectColumns,
                table.schema(),
                table.tableName(),
                where.sql(),
                selectColumns,
                effectiveLimit
        );
        var rows = limitAndMask(queryClient.queryForRows(scope, sql, where.parameters()), effectiveLimit);

        return new DbGroupCountResult(
                scope.environment(),
                databaseAlias(scope),
                table,
                rows.rows(),
                rows.truncated(),
                rows.warnings()
        );
    }

    public DbSampleRowsResult sampleRows(DbToolScope scope, DbSampleRowsRequest request) {
        var table = sqlGuard.normalizeTableRef(scope, request.table());
        var columns = sqlGuard.validateColumns(scope, table, request.columns());
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("db_sample_rows requires explicit projection columns.");
        }

        var where = buildWhereClause(scope, table, "t", request.filters());
        var orderBy = buildOrderByClause(scope, table, "t", request.orderBy());
        var effectiveLimit = normalizePositive(request.limit(), properties.getMaxRows());
        var sql = """
                SELECT %s
                FROM %s.%s t%s%s
                FETCH FIRST %d ROWS ONLY
                """.formatted(
                columns.stream().map(column -> "t." + column).collect(java.util.stream.Collectors.joining(", ")),
                table.schema(),
                table.tableName(),
                where.sql(),
                orderBy,
                effectiveLimit
        );
        var rows = limitAndMask(queryClient.queryForRows(scope, sql, where.parameters()), effectiveLimit);

        return new DbSampleRowsResult(
                scope.environment(),
                databaseAlias(scope),
                table,
                columns,
                rows.rows(),
                rows.truncated(),
                rows.warnings()
        );
    }

    public DbOrphanCheckResult checkOrphans(DbToolScope scope, DbCheckOrphansRequest request) {
        var childTable = sqlGuard.normalizeTableRef(scope, request.childTable());
        var parentTable = sqlGuard.normalizeTableRef(scope, request.parentTable());
        var childColumn = sqlGuard.validateColumn(scope, childTable, request.childColumn());
        var parentColumn = sqlGuard.validateColumn(scope, parentTable, request.parentColumn());
        var childDescription = metadataClient.describeTable(scope.environment(), childTable.schema(), childTable.tableName());
        var sampleColumns = new LinkedHashSet<String>();
        sampleColumns.add(childColumn);
        sampleColumns.addAll(childDescription.primaryKeyColumns());
        for (var filter : safeList(request.childFilters())) {
            if (filter != null && StringUtils.hasText(filter.column())) {
                sampleColumns.add(sqlGuard.validateColumn(scope, childTable, filter.column()));
            }
        }

        var where = buildWhereClause(scope, childTable, "c", request.childFilters());
        var orphanCondition = "c.%s IS NOT NULL AND p.%s IS NULL".formatted(childColumn, parentColumn);
        var whereSql = where.sql().isBlank()
                ? " WHERE " + orphanCondition
                : where.sql() + " AND " + orphanCondition;

        var countSql = """
                SELECT COUNT(*)
                FROM %s.%s c
                LEFT JOIN %s.%s p
                  ON c.%s = p.%s%s
                """.formatted(
                childTable.schema(),
                childTable.tableName(),
                parentTable.schema(),
                parentTable.tableName(),
                childColumn,
                parentColumn,
                whereSql
        );
        var orphanCount = queryClient.queryForCount(scope, countSql, where.parameters());

        var sampleLimit = normalizePositive(request.sampleLimit(), Math.min(properties.getMaxRows(), 10));
        var sampleSql = """
                SELECT %s
                FROM %s.%s c
                LEFT JOIN %s.%s p
                  ON c.%s = p.%s%s
                FETCH FIRST %d ROWS ONLY
                """.formatted(
                sampleColumns.stream().map(column -> "c." + column).collect(java.util.stream.Collectors.joining(", ")),
                childTable.schema(),
                childTable.tableName(),
                parentTable.schema(),
                parentTable.tableName(),
                childColumn,
                parentColumn,
                whereSql,
                sampleLimit
        );
        var sampleRows = limitAndMask(queryClient.queryForRows(scope, sampleSql, where.parameters()), sampleLimit);

        return new DbOrphanCheckResult(
                scope.environment(),
                databaseAlias(scope),
                childTable,
                childColumn,
                parentTable,
                parentColumn,
                orphanCount,
                sampleRows.rows(),
                sampleRows.truncated(),
                sampleRows.warnings()
        );
    }

    public DbRelationshipsResult findRelationships(DbToolScope scope, DbFindRelationshipsRequest request) {
        var includeInferred = Boolean.TRUE.equals(request.includeInferred());
        var effectiveDepth = Math.min(Math.max(normalizePositive(request.depth(), 1), 1), 2);
        var warnings = new ArrayList<String>();
        if (normalizePositive(request.depth(), 1) > 2) {
            warnings.add("Relationship traversal depth was capped at 2.");
        }

        var queue = new ArrayDeque<DbTableRef>();
        for (var table : safeList(request.tables())) {
            queue.add(sqlGuard.normalizeTableRef(scope, table));
        }

        var visited = new LinkedHashSet<DbTableRef>();
        var relationships = new LinkedHashSet<DbRelationshipHint>();
        var currentDepth = 0;

        while (!queue.isEmpty() && currentDepth < effectiveDepth) {
            var levelSize = queue.size();
            for (var index = 0; index < levelSize; index++) {
                var table = queue.removeFirst();
                if (!visited.add(table)) {
                    continue;
                }

                var description = metadataClient.describeTable(scope.environment(), table.schema(), table.tableName());
                relationships.addAll(toDeclaredRelationshipHints(description.importedForeignKeys()));
                relationships.addAll(toDeclaredRelationshipHints(description.exportedForeignKeys()));
                if (includeInferred) {
                    relationships.addAll(toRelationshipHints(description.inferredRelationships()));
                }

                if (currentDepth + 1 < effectiveDepth) {
                    description.importedForeignKeys().forEach(foreignKey ->
                            queue.add(new DbTableRef(foreignKey.targetTable().schema(), foreignKey.targetTable().tableName()))
                    );
                    description.exportedForeignKeys().forEach(foreignKey ->
                            queue.add(new DbTableRef(foreignKey.sourceTable().schema(), foreignKey.sourceTable().tableName()))
                    );
                }
            }
            currentDepth++;
        }

        if (includeInferred) {
            warnings.add("Inferred relationships are naming-based hints and may not represent declared foreign keys.");
        }

        return new DbRelationshipsResult(
                scope.environment(),
                databaseAlias(scope),
                List.copyOf(relationships),
                List.copyOf(warnings)
        );
    }

    public DbJoinCountResult joinCount(DbToolScope scope, DbJoinCountRequest request) {
        var joinPlan = buildJoinPlan(scope, request.tables(), request.joins(), request.filters());
        var count = queryClient.queryForCount(
                scope,
                "SELECT COUNT(*) " + joinPlan.fromClause() + joinPlan.whereClause().sql(),
                joinPlan.whereClause().parameters()
        );

        return new DbJoinCountResult(
                scope.environment(),
                databaseAlias(scope),
                count,
                List.of()
        );
    }

    public DbJoinSampleResult joinSample(DbToolScope scope, DbJoinSampleRequest request) {
        if (safeList(request.columns()).isEmpty()) {
            throw new IllegalArgumentException("db_join_sample requires explicit projected columns.");
        }

        var joinPlan = buildJoinPlan(scope, request.tables(), request.joins(), request.filters());
        var selectColumns = new ArrayList<String>();
        for (var projectedColumn : safeList(request.columns())) {
            if (projectedColumn == null || projectedColumn.column() == null) {
                continue;
            }
            var normalizedTable = sqlGuard.normalizeTableRef(scope, projectedColumn.column().table());
            var alias = joinPlan.aliasByTableKey().get(new TableKey(normalizedTable.schema(), normalizedTable.tableName()));
            var column = sqlGuard.validateColumn(scope, normalizedTable, projectedColumn.column().column());
            selectColumns.add("%s.%s AS %s".formatted(
                    alias,
                    column,
                    projectionAlias(projectedColumn, alias, column)
            ));
        }
        if (selectColumns.isEmpty()) {
            throw new IllegalArgumentException("db_join_sample requires at least one valid projected column.");
        }

        var effectiveLimit = normalizePositive(request.limit(), properties.getMaxRows());
        var sql = """
                SELECT %s
                %s%s
                FETCH FIRST %d ROWS ONLY
                """.formatted(
                String.join(", ", selectColumns),
                joinPlan.fromClause(),
                joinPlan.whereClause().sql(),
                effectiveLimit
        );
        var rows = limitAndMask(queryClient.queryForRows(scope, sql, joinPlan.whereClause().parameters()), effectiveLimit);

        return new DbJoinSampleResult(
                scope.environment(),
                databaseAlias(scope),
                rows.rows(),
                rows.truncated(),
                rows.warnings()
        );
    }

    public DbMappingComparisonResult compareTableToExpectedMapping(
            DbToolScope scope,
            DbMappingComparisonRequest request
    ) {
        var table = sqlGuard.normalizeTableRef(scope, request.actualTable());
        var description = metadataClient.describeTable(scope.environment(), table.schema(), table.tableName());
        var actualColumns = new LinkedHashMap<String, ColumnDefinition>();
        description.columns().forEach(column -> actualColumns.put(column.name(), column));

        var confirmedMatches = new ArrayList<String>();
        var mismatches = new ArrayList<String>();

        for (var expectedColumn : safeList(request.expectedColumns())) {
            compareExpectedColumn(description, actualColumns, expectedColumn, confirmedMatches, mismatches);
        }
        for (var expectedRelationship : safeList(request.expectedRelationships())) {
            compareExpectedRelationship(description, expectedRelationship, confirmedMatches, mismatches);
        }

        return new DbMappingComparisonResult(
                scope.environment(),
                databaseAlias(scope),
                table,
                List.copyOf(confirmedMatches),
                List.copyOf(mismatches),
                List.of()
        );
    }

    public DbReadonlySqlResult executeReadonlySql(DbToolScope scope, DbReadonlySqlRequest request) {
        var validatedSql = sqlGuard.validateReadonlySql(scope, request.sql());
        var effectiveLimit = normalizePositive(request.maxRows(), properties.getMaxRows());
        var limitedSql = "SELECT * FROM (%s) raw_query WHERE ROWNUM <= %d".formatted(validatedSql, effectiveLimit);
        var rows = limitAndMask(queryClient.queryForRows(scope, limitedSql, Map.of()), effectiveLimit);

        return new DbReadonlySqlResult(
                scope.environment(),
                databaseAlias(scope),
                request.reason(),
                rows.rows(),
                rows.truncated(),
                rows.warnings()
        );
    }

    ScoredTableCandidate rankTableCandidate(
            TableMetadata table,
            DbFindTablesRequest request,
            ResolvedDatabaseApplicationScope resolvedScope
    ) {
        var reasons = new ArrayList<String>(resolvedScope.matchedBecause());
        var score = 0;
        var normalizedTableName = table.tableName().toUpperCase(Locale.ROOT);
        var normalizedComment = normalizeText(table.comment());

        if (StringUtils.hasText(request.tableNamePattern())
                && normalizedTableName.contains(request.tableNamePattern().trim().toUpperCase(Locale.ROOT))) {
            reasons.add("table name matched %s".formatted(request.tableNamePattern().trim().toUpperCase(Locale.ROOT)));
            score += 40;
        }

        var hintTokens = normalizedHintTokens(request.entityOrKeywordHint());
        for (var token : hintTokens) {
            if (normalizedTableName.contains(token)) {
                reasons.add("entityOrKeywordHint matched %s".formatted(request.entityOrKeywordHint()));
                score += 25;
            }
            if (normalizedComment.contains(token)) {
                reasons.add("table comment matched %s".formatted(token));
                score += 15;
            }
            if (metadataClient.inferLikelyKeyColumns(table).stream().anyMatch(column -> column.contains(token))) {
                reasons.add("column %s exists".formatted(
                        metadataClient.inferLikelyKeyColumns(table).stream()
                                .filter(column -> column.contains(token))
                                .findFirst()
                                .orElse(token)
                ));
                score += 10;
            }
        }

        score += Math.max(0, 10 - Math.min(table.importedForeignKeyCount(), 10));
        if (!table.primaryKeyColumns().isEmpty()) {
            score += 5;
        }

        return new ScoredTableCandidate(
                score,
                new DbTableCandidate(
                        table.schema(),
                        table.tableName(),
                        table.tableType(),
                        table.comment(),
                        table.columnCount(),
                        table.primaryKeyColumns(),
                        metadataClient.inferLikelyKeyColumns(table),
                        table.importedForeignKeyCount(),
                        table.exportedForeignKeyCount(),
                        deduplicate(reasons)
                )
        );
    }

    ScoredColumnCandidate rankColumnCandidate(
            ColumnMetadata column,
            DbFindColumnsRequest request,
            ResolvedDatabaseApplicationScope resolvedScope
    ) {
        var reasons = new ArrayList<String>(resolvedScope.matchedBecause());
        var score = 0;
        var normalizedColumnName = column.columnName().toUpperCase(Locale.ROOT);
        var normalizedComment = normalizeText(column.comment());

        if (StringUtils.hasText(request.tableNamePattern())
                && column.tableName().toUpperCase(Locale.ROOT).contains(request.tableNamePattern().trim().toUpperCase(Locale.ROOT))) {
            reasons.add("table name matched %s".formatted(request.tableNamePattern().trim().toUpperCase(Locale.ROOT)));
            score += 20;
        }
        if (StringUtils.hasText(request.columnNamePattern())
                && normalizedColumnName.contains(request.columnNamePattern().trim().toUpperCase(Locale.ROOT))) {
            reasons.add("column name matched %s".formatted(request.columnNamePattern().trim().toUpperCase(Locale.ROOT)));
            score += 40;
        }

        var javaFieldSnake = metadataClient.camelToSnakeUpper(request.javaFieldNameHint());
        if (StringUtils.hasText(javaFieldSnake) && normalizedColumnName.contains(javaFieldSnake)) {
            reasons.add("javaFieldNameHint matched %s".formatted(javaFieldSnake));
            score += 35;
        }
        if (StringUtils.hasText(javaFieldSnake) && normalizedComment.contains(javaFieldSnake.replace('_', ' '))) {
            reasons.add("column comment matched %s".formatted(request.javaFieldNameHint()));
            score += 10;
        }

        return new ScoredColumnCandidate(
                score,
                new DbColumnCandidate(
                        column.schema(),
                        column.tableName(),
                        column.columnName(),
                        column.dataType(),
                        column.dataLength(),
                        column.dataPrecision(),
                        column.dataScale(),
                        column.nullable(),
                        column.comment(),
                        deduplicate(reasons)
                )
        );
    }

    private void compareExpectedColumn(
            TableDescriptionMetadata description,
            Map<String, ColumnDefinition> actualColumns,
            ExpectedColumn expectedColumn,
            List<String> confirmedMatches,
            List<String> mismatches
    ) {
        if (expectedColumn == null || !StringUtils.hasText(expectedColumn.expectedColumn())) {
            return;
        }

        var normalizedColumn = sqlGuard.normalizeIdentifier(expectedColumn.expectedColumn());
        var actualColumn = actualColumns.get(normalizedColumn);
        if (actualColumn == null) {
            mismatches.add("Missing expected column `%s`.".formatted(normalizedColumn));
            return;
        }

        confirmedMatches.add("Column `%s` exists.".formatted(normalizedColumn));
        if (StringUtils.hasText(expectedColumn.expectedSqlType())
                && !actualColumn.dataType().equalsIgnoreCase(expectedColumn.expectedSqlType())) {
            mismatches.add("Column `%s` type mismatch: expected `%s`, actual `%s`."
                    .formatted(normalizedColumn, expectedColumn.expectedSqlType(), actualColumn.dataType()));
        }
        if (expectedColumn.nullable() != null && actualColumn.nullable() != expectedColumn.nullable()) {
            mismatches.add("Column `%s` nullability mismatch: expected `%s`, actual `%s`."
                    .formatted(normalizedColumn, expectedColumn.nullable(), actualColumn.nullable()));
        }
        if (Boolean.TRUE.equals(expectedColumn.id())
                && !description.primaryKeyColumns().contains(normalizedColumn)) {
            mismatches.add("Column `%s` was expected to be part of the primary key but is not declared as PK."
                    .formatted(normalizedColumn));
        }
    }

    private void compareExpectedRelationship(
            TableDescriptionMetadata description,
            ExpectedRelationship expectedRelationship,
            List<String> confirmedMatches,
            List<String> mismatches
    ) {
        if (expectedRelationship == null || !StringUtils.hasText(expectedRelationship.expectedJoinColumn())) {
            return;
        }

        var joinColumn = sqlGuard.normalizeIdentifier(expectedRelationship.expectedJoinColumn());
        var targetTableHint = StringUtils.hasText(expectedRelationship.expectedTargetTable())
                ? sqlGuard.normalizeIdentifier(expectedRelationship.expectedTargetTable())
                : null;
        var targetColumnHint = StringUtils.hasText(expectedRelationship.expectedTargetColumn())
                ? sqlGuard.normalizeIdentifier(expectedRelationship.expectedTargetColumn())
                : null;

        var matched = description.importedForeignKeys().stream().anyMatch(foreignKey ->
                foreignKey.sourceColumns().contains(joinColumn)
                        && (targetTableHint == null || foreignKey.targetTable().tableName().equals(targetTableHint))
                        && (targetColumnHint == null || foreignKey.targetColumns().contains(targetColumnHint))
        );

        if (matched) {
            confirmedMatches.add("Relationship for join column `%s` is declared.".formatted(joinColumn));
        }
        else {
            mismatches.add("Expected relationship for join column `%s` was not confirmed in visible foreign keys."
                    .formatted(joinColumn));
        }
    }

    private JoinPlan buildJoinPlan(
            DbToolScope scope,
            List<DbTableRef> tables,
            List<DbJoinCondition> joins,
            List<DbQualifiedFilter> filters
    ) {
        var normalizedTables = safeList(tables).stream()
                .map(table -> sqlGuard.normalizeTableRef(scope, table))
                .toList();
        if (normalizedTables.isEmpty()) {
            throw new IllegalArgumentException("Join tools require at least one table.");
        }
        if (normalizedTables.size() > 1 && safeList(joins).isEmpty()) {
            throw new IllegalArgumentException("Join tools require explicit join conditions when multiple tables are used.");
        }

        var aliasByTableKey = new LinkedHashMap<TableKey, String>();
        for (var index = 0; index < normalizedTables.size(); index++) {
            var table = normalizedTables.get(index);
            aliasByTableKey.put(new TableKey(table.schema(), table.tableName()), "t" + index);
        }

        var fromClause = new StringBuilder("FROM ");
        var firstTable = normalizedTables.get(0);
        fromClause.append(firstTable.schema()).append('.').append(firstTable.tableName()).append(" t0");

        for (var join : safeList(joins)) {
            if (join == null) {
                continue;
            }

            var leftTable = sqlGuard.normalizeTableRef(scope, join.left().table());
            var rightTable = sqlGuard.normalizeTableRef(scope, join.right().table());
            var rightAlias = aliasByTableKey.get(new TableKey(rightTable.schema(), rightTable.tableName()));
            if (rightAlias == null) {
                throw new IllegalArgumentException("Join condition references a table outside the selected table list.");
            }

            var leftAlias = aliasByTableKey.get(new TableKey(leftTable.schema(), leftTable.tableName()));
            if (leftAlias == null) {
                throw new IllegalArgumentException("Join condition references a table outside the selected table list.");
            }

            var leftColumn = sqlGuard.validateColumn(scope, leftTable, join.left().column());
            var rightColumn = sqlGuard.validateColumn(scope, rightTable, join.right().column());
            if (fromClause.indexOf(" " + rightAlias) < 0) {
                fromClause.append(join.type() == JoinType.LEFT ? " LEFT JOIN " : " INNER JOIN ")
                        .append(rightTable.schema())
                        .append('.')
                        .append(rightTable.tableName())
                        .append(' ')
                        .append(rightAlias)
                        .append(" ON ")
                        .append(leftAlias).append('.').append(leftColumn)
                        .append(" = ")
                        .append(rightAlias).append('.').append(rightColumn);
            }
        }

        var whereClause = buildQualifiedWhereClause(scope, aliasByTableKey, filters);
        return new JoinPlan(fromClause.toString(), aliasByTableKey, whereClause);
    }

    private WhereClause buildKeyWhereClause(
            DbToolScope scope,
            DbTableRef table,
            String alias,
            List<DbKeyValue> keyValues
    ) {
        var conditions = new ArrayList<String>();
        var parameters = new LinkedHashMap<String, Object>();
        var appliedFilters = new ArrayList<String>();
        var index = 0;

        for (var keyValue : keyValues) {
            if (keyValue == null) {
                continue;
            }
            var column = sqlGuard.validateColumn(scope, table, keyValue.column());
            var parameterName = "p" + index++;
            conditions.add("%s.%s = :%s".formatted(alias, column, parameterName));
            parameters.put(parameterName, keyValue.value());
            appliedFilters.add("%s = ?".formatted(column));
        }

        return new WhereClause(
                conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions),
                parameters,
                appliedFilters
        );
    }

    private WhereClause buildWhereClause(
            DbToolScope scope,
            DbTableRef table,
            String alias,
            List<DbFilter> filters
    ) {
        var conditions = new ArrayList<String>();
        var parameters = new LinkedHashMap<String, Object>();
        var appliedFilters = new ArrayList<String>();
        var index = 0;

        for (var filter : safeList(filters)) {
            if (filter == null || filter.operator() == null) {
                continue;
            }

            var column = sqlGuard.validateColumn(scope, table, filter.column());
            var fragment = filterClause("%s.%s".formatted(alias, column), filter.operator(), filter.values(), index);
            conditions.add(fragment.sql());
            parameters.putAll(fragment.parameters());
            appliedFilters.add(column + " " + filter.operator());
            index = fragment.nextIndex();
        }

        return new WhereClause(
                conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions),
                parameters,
                appliedFilters
        );
    }

    private WhereClause buildQualifiedWhereClause(
            DbToolScope scope,
            Map<TableKey, String> aliasByTableKey,
            List<DbQualifiedFilter> filters
    ) {
        var conditions = new ArrayList<String>();
        var parameters = new LinkedHashMap<String, Object>();
        var appliedFilters = new ArrayList<String>();
        var index = 0;

        for (var filter : safeList(filters)) {
            if (filter == null || filter.column() == null || filter.operator() == null) {
                continue;
            }

            var normalizedTable = sqlGuard.normalizeTableRef(scope, filter.column().table());
            var alias = aliasByTableKey.get(new TableKey(normalizedTable.schema(), normalizedTable.tableName()));
            if (alias == null) {
                throw new IllegalArgumentException("Qualified filter references a table outside the selected join scope.");
            }

            var column = sqlGuard.validateColumn(scope, normalizedTable, filter.column().column());
            var fragment = filterClause("%s.%s".formatted(alias, column), filter.operator(), filter.values(), index);
            conditions.add(fragment.sql());
            parameters.putAll(fragment.parameters());
            appliedFilters.add(normalizedTable.tableName() + "." + column + " " + filter.operator());
            index = fragment.nextIndex();
        }

        return new WhereClause(
                conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions),
                parameters,
                appliedFilters
        );
    }

    SqlFragment filterClause(String qualifiedColumn, DbOperator operator, List<String> values, int startIndex) {
        var parameters = new LinkedHashMap<String, Object>();
        var safeValues = safeList(values);
        var nextIndex = startIndex;

        return switch (operator) {
            case EQ -> {
                var parameterName = "p" + nextIndex++;
                parameters.put(parameterName, singleValue(operator, safeValues));
                yield new SqlFragment("%s = :%s".formatted(qualifiedColumn, parameterName), parameters, nextIndex);
            }
            case NE -> {
                var parameterName = "p" + nextIndex++;
                parameters.put(parameterName, singleValue(operator, safeValues));
                yield new SqlFragment("%s <> :%s".formatted(qualifiedColumn, parameterName), parameters, nextIndex);
            }
            case GT -> {
                var parameterName = "p" + nextIndex++;
                parameters.put(parameterName, singleValue(operator, safeValues));
                yield new SqlFragment("%s > :%s".formatted(qualifiedColumn, parameterName), parameters, nextIndex);
            }
            case GTE -> {
                var parameterName = "p" + nextIndex++;
                parameters.put(parameterName, singleValue(operator, safeValues));
                yield new SqlFragment("%s >= :%s".formatted(qualifiedColumn, parameterName), parameters, nextIndex);
            }
            case LT -> {
                var parameterName = "p" + nextIndex++;
                parameters.put(parameterName, singleValue(operator, safeValues));
                yield new SqlFragment("%s < :%s".formatted(qualifiedColumn, parameterName), parameters, nextIndex);
            }
            case LTE -> {
                var parameterName = "p" + nextIndex++;
                parameters.put(parameterName, singleValue(operator, safeValues));
                yield new SqlFragment("%s <= :%s".formatted(qualifiedColumn, parameterName), parameters, nextIndex);
            }
            case LIKE -> {
                var parameterName = "p" + nextIndex++;
                parameters.put(parameterName, singleValue(operator, safeValues));
                yield new SqlFragment("UPPER(%s) LIKE UPPER(:%s)".formatted(qualifiedColumn, parameterName), parameters, nextIndex);
            }
            case STARTS_WITH -> {
                var parameterName = "p" + nextIndex++;
                parameters.put(parameterName, singleValue(operator, safeValues) + "%");
                yield new SqlFragment("UPPER(%s) LIKE UPPER(:%s)".formatted(qualifiedColumn, parameterName), parameters, nextIndex);
            }
            case IN, NOT_IN -> {
                if (safeValues.isEmpty()) {
                    throw new IllegalArgumentException(operator + " filter requires at least one value.");
                }
                var parameterNames = new ArrayList<String>();
                for (var value : safeValues) {
                    var parameterName = "p" + nextIndex++;
                    parameters.put(parameterName, value);
                    parameterNames.add(":" + parameterName);
                }
                yield new SqlFragment(
                        "%s %s (%s)".formatted(
                                qualifiedColumn,
                                operator == DbOperator.IN ? "IN" : "NOT IN",
                                String.join(", ", parameterNames)
                        ),
                        parameters,
                        nextIndex
                );
            }
            case IS_NULL -> new SqlFragment("%s IS NULL".formatted(qualifiedColumn), parameters, nextIndex);
            case IS_NOT_NULL -> new SqlFragment("%s IS NOT NULL".formatted(qualifiedColumn), parameters, nextIndex);
            case BETWEEN -> {
                if (safeValues.size() != 2) {
                    throw new IllegalArgumentException("BETWEEN filter requires exactly two values.");
                }
                var leftParameter = "p" + nextIndex++;
                var rightParameter = "p" + nextIndex++;
                parameters.put(leftParameter, safeValues.get(0));
                parameters.put(rightParameter, safeValues.get(1));
                yield new SqlFragment(
                        "%s BETWEEN :%s AND :%s".formatted(qualifiedColumn, leftParameter, rightParameter),
                        parameters,
                        nextIndex
                );
            }
        };
    }

    private String buildOrderByClause(
            DbToolScope scope,
            DbTableRef table,
            String alias,
            List<DbOrderBy> orderBy
    ) {
        var clauses = new ArrayList<String>();
        for (var sort : safeList(orderBy)) {
            if (sort == null || !StringUtils.hasText(sort.column())) {
                continue;
            }
            var column = sqlGuard.validateColumn(scope, table, sort.column());
            var direction = sort.direction() == SortDirection.DESC ? "DESC" : "ASC";
            clauses.add("%s.%s %s".formatted(alias, column, direction));
        }

        return clauses.isEmpty() ? "" : " ORDER BY " + String.join(", ", clauses);
    }

    private DatabaseResultLimiter.LimitedRowsResult limitAndMask(
            List<Map<String, Object>> rows,
            Integer requestedMaxRows
    ) {
        return resultLimiter.limitRows(resultMasker.maskRows(rows), requestedMaxRows);
    }

    private List<DbForeignKeyDescription> toForeignKeyDescriptions(List<ForeignKeyMetadata> foreignKeys) {
        return foreignKeys.stream()
                .map(foreignKey -> new DbForeignKeyDescription(
                        foreignKey.constraintName(),
                        new DbTableRef(foreignKey.sourceTable().schema(), foreignKey.sourceTable().tableName()),
                        foreignKey.sourceColumns(),
                        new DbTableRef(foreignKey.targetTable().schema(), foreignKey.targetTable().tableName()),
                        foreignKey.targetColumns()
                ))
                .toList();
    }

    private List<DbRelationshipHint> toRelationshipHints(Collection<RelationshipMetadata> relationships) {
        return relationships.stream()
                .map(relationship -> new DbRelationshipHint(
                        new DbTableRef(relationship.sourceTable().schema(), relationship.sourceTable().tableName()),
                        relationship.sourceColumn(),
                        new DbTableRef(relationship.targetTable().schema(), relationship.targetTable().tableName()),
                        relationship.targetColumn(),
                        relationship.evidence(),
                        relationship.declared()
                ))
                .toList();
    }

    private List<DbRelationshipHint> toDeclaredRelationshipHints(List<ForeignKeyMetadata> foreignKeys) {
        return foreignKeys.stream()
                .map(foreignKey -> new DbRelationshipHint(
                        new DbTableRef(foreignKey.sourceTable().schema(), foreignKey.sourceTable().tableName()),
                        foreignKey.sourceColumns().isEmpty() ? null : foreignKey.sourceColumns().get(0),
                        new DbTableRef(foreignKey.targetTable().schema(), foreignKey.targetTable().tableName()),
                        foreignKey.targetColumns().isEmpty() ? null : foreignKey.targetColumns().get(0),
                        "Declared foreign key " + foreignKey.constraintName(),
                        true
                ))
                .toList();
    }

    private String databaseAlias(DbToolScope scope) {
        return scopeResolver.requiredEnvironment(scope.environment()).getDatabaseAlias();
    }

    private String projectionAlias(DbProjectedColumn projectedColumn, String tableAlias, String column) {
        if (StringUtils.hasText(projectedColumn.alias())) {
            var normalized = projectedColumn.alias()
                    .trim()
                    .toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9_$#]+", "_");
            if (normalized.matches("[A-Z][A-Z0-9_$#]*")) {
                return normalized;
            }
        }
        return (tableAlias + "_" + column).toUpperCase(Locale.ROOT);
    }

    private String singleValue(DbOperator operator, List<String> values) {
        if (values.size() != 1) {
            throw new IllegalArgumentException(operator + " filter requires exactly one value.");
        }
        return values.get(0);
    }

    private List<String> mergeWarnings(List<String> warnings) {
        return warnings != null ? List.copyOf(warnings) : List.of();
    }

    private List<String> normalizedHintTokens(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }

        var snakeCase = metadataClient.camelToSnakeUpper(value);
        if (!StringUtils.hasText(snakeCase)) {
            return List.of();
        }
        var tokens = new LinkedHashSet<String>();
        tokens.add(snakeCase);
        for (var token : snakeCase.split("_")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    private String normalizeText(String value) {
        return value != null ? value.toUpperCase(Locale.ROOT) : "";
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private int normalizeDiscoveryLimit(Integer requestedLimit, int configuredLimit) {
        return Math.min(normalizePositive(requestedLimit, configuredLimit), configuredLimit);
    }

    private int normalizePositive(Integer requestedLimit, int defaultValue) {
        return requestedLimit != null && requestedLimit > 0 ? requestedLimit : defaultValue;
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private List<String> deduplicate(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    record SqlFragment(
            String sql,
            Map<String, Object> parameters,
            int nextIndex
    ) {
    }

    record WhereClause(
            String sql,
            Map<String, Object> parameters,
            List<String> appliedFilters
    ) {
    }

    record JoinPlan(
            String fromClause,
            Map<TableKey, String> aliasByTableKey,
            WhereClause whereClause
    ) {
    }

    record ScoredTableCandidate(
            int score,
            DbTableCandidate candidate
    ) {
    }

    record ScoredColumnCandidate(
            int score,
            DbColumnCandidate candidate
    ) {
    }
}
