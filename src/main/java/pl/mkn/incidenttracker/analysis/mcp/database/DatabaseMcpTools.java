package pl.mkn.incidenttracker.analysis.mcp.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.adapter.database.DatabaseToolService;

import static pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolNames.*;
import static pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolDtos.*;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseMcpTools {

    private static final String REASON_DESCRIPTION = "Short reason in Polish for the operator. Use one practical sentence.";

    private final DatabaseToolService databaseToolService;

    @Tool(
            name = GET_SCOPE,
            description = """
                    Returns the current incident database scope for the hidden session environment.
                    Use this to inspect configured application-to-schema mappings, allowed schemas and safety rules
                    before broader DB diagnostics. The environment is taken from hidden ToolContext.
                    """
    )
    public DbScopeResult getScope(
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var startedAt = System.nanoTime();
        logRequest(GET_SCOPE, scope, "request=no-args");
        var result = databaseToolService.getScope(scope);
        logResult(
                GET_SCOPE,
                scope,
                startedAt,
                "applications=%d allowedSchemas=%d".formatted(result.applications().size(), result.allowedSchemas().size())
        );
        return result;
    }

    @Tool(
            name = FIND_TABLES,
            description = """
                    Finds ranked Oracle table/view candidates for an application on the current incident environment.
                    The environment is taken from hidden ToolContext. applicationNamePattern should be an application,
                    deployment, container, service or GitLab project name inferred from incident evidence. The backend
                    resolves it to a configured Oracle owner/schema and searches allowed tables/views. Returns lightweight
                    candidates only, not full schemas. For JPA/repository symptoms, first try to ground the entity/table
                    mapping from deterministic GitLab evidence or enabled GitLab tools; use this as fallback when that
                    mapping is still incomplete.
                    """
    )
    public DbTableSearchResult findTables(
            @ToolParam(required = false, description = "Application, deployment, container, service or GitLab project name inferred from incident evidence.")
            String applicationNamePattern,
            @ToolParam(required = false, description = "Optional table/view name fragment.")
            String tableNamePattern,
            @ToolParam(required = false, description = "Optional entity, keyword or domain hint used for ranking.")
            String entityOrKeywordHint,
            @ToolParam(required = false, description = "Maximum number of ranked candidates to return.")
            Integer limit,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbFindTablesRequest(applicationNamePattern, tableNamePattern, entityOrKeywordHint, limit);
        var startedAt = System.nanoTime();
        logRequest(
                FIND_TABLES,
                scope,
                "applicationNamePattern=%s tableNamePattern=%s entityOrKeywordHint=%s limit=%s"
                        .formatted(applicationNamePattern, tableNamePattern, entityOrKeywordHint, limit)
        );
        var result = databaseToolService.findTables(scope, request);
        logResult(
                FIND_TABLES,
                scope,
                startedAt,
                "resolvedApplication=%s resolvedSchemas=%s candidateCount=%d truncated=%s"
                        .formatted(result.resolvedApplication(), result.resolvedSchemas(), result.candidates().size(), result.truncated())
        );
        return result;
    }

    @Tool(
            name = FIND_COLUMNS,
            description = """
                    Finds ranked Oracle column candidates for an application on the current incident environment.
                    Use applicationNamePattern from evidence and optional table/column/Java field hints to locate ID,
                    business key, tenant, status, state, soft-delete, validity, event or correlation columns. Prefer
                    Java field hints and relation hints from entity/repository code over guessed column names.
                    """
    )
    public DbColumnSearchResult findColumns(
            @ToolParam(required = false, description = "Application, deployment, container, service or GitLab project name inferred from incident evidence.")
            String applicationNamePattern,
            @ToolParam(required = false, description = "Optional table/view name fragment.")
            String tableNamePattern,
            @ToolParam(required = false, description = "Optional column name fragment.")
            String columnNamePattern,
            @ToolParam(required = false, description = "Optional Java field hint, for example correlationId or statusCode.")
            String javaFieldNameHint,
            @ToolParam(required = false, description = "Maximum number of ranked candidates to return.")
            Integer limit,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbFindColumnsRequest(
                applicationNamePattern,
                tableNamePattern,
                columnNamePattern,
                javaFieldNameHint,
                limit
        );
        var startedAt = System.nanoTime();
        logRequest(
                FIND_COLUMNS,
                scope,
                "applicationNamePattern=%s tableNamePattern=%s columnNamePattern=%s javaFieldNameHint=%s limit=%s"
                        .formatted(applicationNamePattern, tableNamePattern, columnNamePattern, javaFieldNameHint, limit)
        );
        var result = databaseToolService.findColumns(scope, request);
        logResult(
                FIND_COLUMNS,
                scope,
                startedAt,
                "resolvedApplication=%s resolvedSchemas=%s candidateCount=%d truncated=%s"
                        .formatted(result.resolvedApplication(), result.resolvedSchemas(), result.candidates().size(), result.truncated())
        );
        return result;
    }

    @Tool(
            name = DESCRIBE_TABLE,
            description = """
                    Describes an exact Oracle schema.table on the current incident environment.
                    Use this only after discovery or when a prior result already grounded the exact schema.table.
                    Returns columns, PK/FK, indexes and lightweight relationship hints.
                    """
    )
    public DbTableDescription describeTable(
            @ToolParam(description = "Exact schema.table reference returned by prior DB discovery or confirmed evidence.")
            DbTableRef table,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var startedAt = System.nanoTime();
        logRequest(DESCRIBE_TABLE, scope, "table=%s".formatted(table));
        var result = databaseToolService.describeTable(scope, new DbDescribeTableRequest(table));
        logResult(
                DESCRIBE_TABLE,
                scope,
                startedAt,
                "table=%s.%s columnCount=%d fkCount=%d".formatted(
                        result.schema(),
                        result.tableName(),
                        result.columns().size(),
                        result.importedForeignKeys().size() + result.exportedForeignKeys().size()
                )
        );
        return result;
    }

    @Tool(
            name = EXISTS_BY_KEY,
            description = """
                    Checks whether an exact Oracle schema.table row exists by primary key or business key on the current
                    incident environment. Use this for direct entity existence checks after exact schema.table is known.
                    """
    )
    public DbExistsResult existsByKey(
            @ToolParam(description = "Exact schema.table reference returned by prior DB discovery or confirmed evidence.")
            DbTableRef table,
            @ToolParam(description = "Key column/value pairs used for exact row existence checking.")
            java.util.List<DbKeyValue> keyValues,
            @ToolParam(required = false, description = "Optional projection columns to return when the row exists. Prefer a small technical projection.")
            java.util.List<String> projectionColumns,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbExistsByKeyRequest(table, keyValues, projectionColumns);
        var startedAt = System.nanoTime();
        logRequest(
                EXISTS_BY_KEY,
                scope,
                "table=%s keyCount=%d projectionColumnCount=%d".formatted(
                        table,
                        keyValues != null ? keyValues.size() : 0,
                        projectionColumns != null ? projectionColumns.size() : 0
                )
        );
        var result = databaseToolService.existsByKey(scope, request);
        logResult(
                EXISTS_BY_KEY,
                scope,
                startedAt,
                "table=%s exists=%s count=%d projectionRows=%d".formatted(
                        result.table(),
                        result.exists(),
                        result.count(),
                        result.projection().size()
                )
        );
        return result;
    }

    @Tool(
            name = COUNT_ROWS,
            description = """
                    Counts rows in an exact Oracle schema.table on the current incident environment using typed filters.
                    Use this before sampling rows to confirm key-only versus full-predicate behavior.
                    """
    )
    public DbCountResult countRows(
            @ToolParam(description = "Exact schema.table reference returned by prior DB discovery or confirmed evidence.")
            DbTableRef table,
            @ToolParam(required = false, description = "Typed filters applied with bind parameters.")
            java.util.List<DbFilter> filters,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbCountRowsRequest(table, filters);
        var startedAt = System.nanoTime();
        logRequest(
                COUNT_ROWS,
                scope,
                "table=%s filterCount=%d".formatted(table, filters != null ? filters.size() : 0)
        );
        var result = databaseToolService.countRows(scope, request);
        logResult(
                COUNT_ROWS,
                scope,
                startedAt,
                "table=%s count=%d appliedFilters=%d".formatted(
                        result.table(),
                        result.count(),
                        result.appliedFilters().size()
                )
        );
        return result;
    }

    @Tool(
            name = GROUP_COUNT,
            description = """
                    Groups and counts rows in an exact Oracle schema.table on the current incident environment.
                    Use this for status, state, tenant, type, active/deleted or error-code distributions.
                    """
    )
    public DbGroupCountResult groupCount(
            @ToolParam(description = "Exact schema.table reference returned by prior DB discovery or confirmed evidence.")
            DbTableRef table,
            @ToolParam(description = "Columns used in GROUP BY.")
            java.util.List<String> groupByColumns,
            @ToolParam(required = false, description = "Typed filters applied before grouping.")
            java.util.List<DbFilter> filters,
            @ToolParam(required = false, description = "Maximum number of grouped rows to return.")
            Integer limit,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbGroupCountRequest(table, groupByColumns, filters, limit);
        var startedAt = System.nanoTime();
        logRequest(
                GROUP_COUNT,
                scope,
                "table=%s groupByCount=%d filterCount=%d limit=%s".formatted(
                        table,
                        groupByColumns != null ? groupByColumns.size() : 0,
                        filters != null ? filters.size() : 0,
                        limit
                )
        );
        var result = databaseToolService.groupCount(scope, request);
        logResult(
                GROUP_COUNT,
                scope,
                startedAt,
                "table=%s groups=%d truncated=%s".formatted(result.table(), result.groups().size(), result.truncated())
        );
        return result;
    }

    @Tool(
            name = SAMPLE_ROWS,
            description = """
                    Returns a small explicit technical projection from an exact Oracle schema.table on the current incident
                    environment. Use only after typed count/group checks and always provide explicit columns.
                    """
    )
    public DbSampleRowsResult sampleRows(
            @ToolParam(description = "Exact schema.table reference returned by prior DB discovery or confirmed evidence.")
            DbTableRef table,
            @ToolParam(description = "Explicit projection columns. SELECT * is not supported.")
            java.util.List<String> columns,
            @ToolParam(required = false, description = "Typed filters applied with bind parameters.")
            java.util.List<DbFilter> filters,
            @ToolParam(required = false, description = "Optional ORDER BY columns.")
            java.util.List<DbOrderBy> orderBy,
            @ToolParam(required = false, description = "Maximum number of rows to return.")
            Integer limit,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbSampleRowsRequest(table, columns, filters, orderBy, limit);
        var startedAt = System.nanoTime();
        logRequest(
                SAMPLE_ROWS,
                scope,
                "table=%s columnCount=%d filterCount=%d orderByCount=%d limit=%s".formatted(
                        table,
                        columns != null ? columns.size() : 0,
                        filters != null ? filters.size() : 0,
                        orderBy != null ? orderBy.size() : 0,
                        limit
                )
        );
        var result = databaseToolService.sampleRows(scope, request);
        logResult(
                SAMPLE_ROWS,
                scope,
                startedAt,
                "table=%s rowCount=%d truncated=%s".formatted(result.table(), result.rows().size(), result.truncated())
        );
        return result;
    }

    @Tool(
            name = CHECK_ORPHANS,
            description = """
                    Checks for orphan child rows by comparing an exact child schema.table reference to an exact parent
                    schema.table reference on the current incident environment. Use this for stale or missing references.
                    """
    )
    public DbOrphanCheckResult checkOrphans(
            @ToolParam(description = "Exact child schema.table reference.")
            DbTableRef childTable,
            @ToolParam(description = "Child reference column.")
            String childColumn,
            @ToolParam(description = "Exact parent schema.table reference.")
            DbTableRef parentTable,
            @ToolParam(description = "Parent key column.")
            String parentColumn,
            @ToolParam(required = false, description = "Optional filters applied to child rows before orphan checking.")
            java.util.List<DbFilter> childFilters,
            @ToolParam(required = false, description = "Maximum number of sample orphan rows to return.")
            Integer sampleLimit,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbCheckOrphansRequest(
                childTable,
                childColumn,
                parentTable,
                parentColumn,
                childFilters,
                sampleLimit
        );
        var startedAt = System.nanoTime();
        logRequest(
                CHECK_ORPHANS,
                scope,
                "childTable=%s childColumn=%s parentTable=%s parentColumn=%s filterCount=%d sampleLimit=%s".formatted(
                        childTable,
                        childColumn,
                        parentTable,
                        parentColumn,
                        childFilters != null ? childFilters.size() : 0,
                        sampleLimit
                )
        );
        var result = databaseToolService.checkOrphans(scope, request);
        logResult(
                CHECK_ORPHANS,
                scope,
                startedAt,
                "childTable=%s parentTable=%s orphanCount=%d sampleRows=%d truncated=%s".formatted(
                        result.childTable(),
                        result.parentTable(),
                        result.orphanCount(),
                        result.sampleRows().size(),
                        result.truncated()
                )
        );
        return result;
    }

    @Tool(
            name = FIND_RELATIONSHIPS,
            description = """
                    Finds declared and optionally inferred relationships for exact Oracle schema.table references on the
                    current incident environment. Prefer this before broader join exploration when relation structure is unclear.
                    """
    )
    public DbRelationshipsResult findRelationships(
            @ToolParam(description = "Exact schema.table references to inspect.")
            java.util.List<DbTableRef> tables,
            @ToolParam(required = false, description = "Traversal depth. The backend caps this to 2.")
            Integer depth,
            @ToolParam(required = false, description = "Whether to include inferred *_ID naming-based hints in addition to declared foreign keys.")
            Boolean includeInferred,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbFindRelationshipsRequest(tables, depth, includeInferred);
        var startedAt = System.nanoTime();
        logRequest(
                FIND_RELATIONSHIPS,
                scope,
                "tableCount=%d depth=%s includeInferred=%s".formatted(
                        tables != null ? tables.size() : 0,
                        depth,
                        includeInferred
                )
        );
        var result = databaseToolService.findRelationships(scope, request);
        logResult(
                FIND_RELATIONSHIPS,
                scope,
                startedAt,
                "relationshipCount=%d warnings=%d".formatted(result.relationships().size(), result.warnings().size())
        );
        return result;
    }

    @Tool(
            name = JOIN_COUNT,
            description = """
                    Counts rows for an exact join plan across one or more Oracle schema.table references on the current
                    incident environment. Use typed join conditions and typed filters; do not use this for browsing.
                    """
    )
    public DbJoinCountResult joinCount(
            @ToolParam(description = "Exact schema.table references participating in the join.")
            java.util.List<DbTableRef> tables,
            @ToolParam(description = "Explicit join conditions. Cross joins are not allowed for multiple tables.")
            java.util.List<DbJoinCondition> joins,
            @ToolParam(required = false, description = "Typed qualified filters applied with bind parameters.")
            java.util.List<DbQualifiedFilter> filters,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbJoinCountRequest(tables, joins, filters);
        var startedAt = System.nanoTime();
        logRequest(
                JOIN_COUNT,
                scope,
                "tableCount=%d joinCount=%d filterCount=%d".formatted(
                        tables != null ? tables.size() : 0,
                        joins != null ? joins.size() : 0,
                        filters != null ? filters.size() : 0
                )
        );
        var result = databaseToolService.joinCount(scope, request);
        logResult(
                JOIN_COUNT,
                scope,
                startedAt,
                "count=%d warnings=%d".formatted(result.count(), result.warnings().size())
        );
        return result;
    }

    @Tool(
            name = JOIN_SAMPLE,
            description = """
                    Returns a small explicit technical projection from an exact join plan across one or more Oracle
                    schema.table references on the current incident environment. Always provide explicit projected columns.
                    """
    )
    public DbJoinSampleResult joinSample(
            @ToolParam(description = "Exact schema.table references participating in the join.")
            java.util.List<DbTableRef> tables,
            @ToolParam(description = "Explicit join conditions. Cross joins are not allowed for multiple tables.")
            java.util.List<DbJoinCondition> joins,
            @ToolParam(description = "Explicit projected columns for the joined sample.")
            java.util.List<DbProjectedColumn> columns,
            @ToolParam(required = false, description = "Typed qualified filters applied with bind parameters.")
            java.util.List<DbQualifiedFilter> filters,
            @ToolParam(required = false, description = "Maximum number of rows to return.")
            Integer limit,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbJoinSampleRequest(tables, joins, columns, filters, limit);
        var startedAt = System.nanoTime();
        logRequest(
                JOIN_SAMPLE,
                scope,
                "tableCount=%d joinCount=%d columnCount=%d filterCount=%d limit=%s".formatted(
                        tables != null ? tables.size() : 0,
                        joins != null ? joins.size() : 0,
                        columns != null ? columns.size() : 0,
                        filters != null ? filters.size() : 0,
                        limit
                )
        );
        var result = databaseToolService.joinSample(scope, request);
        logResult(
                JOIN_SAMPLE,
                scope,
                startedAt,
                "rowCount=%d truncated=%s warnings=%d".formatted(
                        result.rows().size(),
                        result.truncated(),
                        result.warnings().size()
                )
        );
        return result;
    }

    @Tool(
            name = COMPARE_TABLE_TO_EXPECTED_MAPPING,
            description = """
                    Compares an exact Oracle schema.table to an expected ORM-style mapping hint on the current incident
                    environment. Use this after data checks or when mapping/schema symptoms are explicit in evidence.
                    """
    )
    public DbMappingComparisonResult compareTableToExpectedMapping(
            @ToolParam(description = "Exact schema.table reference.")
            DbTableRef actualTable,
            @ToolParam(required = false, description = "Expected mapped columns from code or evidence.")
            java.util.List<ExpectedColumn> expectedColumns,
            @ToolParam(required = false, description = "Expected relationships from code or evidence.")
            java.util.List<ExpectedRelationship> expectedRelationships,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbMappingComparisonRequest(actualTable, expectedColumns, expectedRelationships);
        var startedAt = System.nanoTime();
        logRequest(
                COMPARE_TABLE_TO_EXPECTED_MAPPING,
                scope,
                "actualTable=%s expectedColumnCount=%d expectedRelationshipCount=%d".formatted(
                        actualTable,
                        expectedColumns != null ? expectedColumns.size() : 0,
                        expectedRelationships != null ? expectedRelationships.size() : 0
                )
        );
        var result = databaseToolService.compareTableToExpectedMapping(scope, request);
        logResult(
                COMPARE_TABLE_TO_EXPECTED_MAPPING,
                scope,
                startedAt,
                "actualTable=%s confirmedMatches=%d mismatches=%d".formatted(
                        result.actualTable(),
                        result.confirmedMatches().size(),
                        result.mismatches().size()
                )
        );
        return result;
    }

    @Tool(
            name = EXECUTE_READONLY_SQL,
            description = """
                    Executes read-only SQL on the current incident environment only when explicitly enabled by backend
                    configuration. Prefer typed DB tools over raw SQL. The backend enforces read-only guards, bind-safe limits,
                    masking and result truncation.
                    """
    )
    public DbReadonlySqlResult executeReadonlySql(
            @ToolParam(description = "Single SELECT or WITH ... SELECT statement. Semicolons and mutating SQL are forbidden.")
            String sql,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            @ToolParam(required = false, description = "Maximum number of rows to return.")
            Integer maxRows,
            ToolContext toolContext
    ) {
        var scope = DbToolScope.from(toolContext);
        var request = new DbReadonlySqlRequest(sql, reason, maxRows);
        var startedAt = System.nanoTime();
        logRequest(
                EXECUTE_READONLY_SQL,
                scope,
                "reason=%s maxRows=%s".formatted(reason, maxRows)
        );
        var result = databaseToolService.executeReadonlySql(scope, request);
        logResult(
                EXECUTE_READONLY_SQL,
                scope,
                startedAt,
                "rowCount=%d truncated=%s warnings=%d".formatted(
                        result.rows().size(),
                        result.truncated(),
                        result.warnings().size()
                )
        );
        return result;
    }

    private void logRequest(String toolName, DbToolScope scope, String details) {
        log.info(
                "Tool request [{}] correlationId={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} details={}",
                toolName,
                scope.correlationId(),
                scope.environment(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                details
        );
    }

    private void logResult(String toolName, DbToolScope scope, long startedAt, String details) {
        log.info(
                "Tool result [{}] correlationId={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} durationMs={} details={}",
                toolName,
                scope.correlationId(),
                scope.environment(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                (System.nanoTime() - startedAt) / 1_000_000,
                details
        );
    }
}
