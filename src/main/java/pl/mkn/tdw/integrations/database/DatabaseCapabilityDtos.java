package pl.mkn.tdw.integrations.database;

import java.util.List;
import java.util.Map;

public final class DatabaseCapabilityDtos {

    private DatabaseCapabilityDtos() {
    }

    public record DbApplicationScopeInfo(
            String applicationAlias,
            String schema,
            List<String> relatedSchemas,
            String description,
            List<String> patterns
    ) {
    }

    public record DbCheckOrphansRequest(
            DbTableRef childTable,
            String childColumn,
            DbTableRef parentTable,
            String parentColumn,
            List<DbFilter> childFilters,
            Integer sampleLimit
    ) {
    }

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

    public record DbColumnRef(
            DbTableRef table,
            String column
    ) {
    }

    public record DbColumnSearchResult(
            String environment,
            String databaseAlias,
            String resolvedApplication,
            List<String> resolvedSchemas,
            List<DbColumnCandidate> candidates,
            boolean truncated,
            List<String> warnings
    ) {
    }

    public record DbCountResult(
            String environment,
            String databaseAlias,
            DbTableRef table,
            long count,
            List<String> appliedFilters,
            List<String> warnings
    ) {
    }

    public record DbCountRowsRequest(
            DbTableRef table,
            List<DbFilter> filters
    ) {
    }

    public record DbDescribeTableRequest(
            DbTableRef table
    ) {
    }

    public record DbExistsByKeyRequest(
            DbTableRef table,
            List<DbKeyValue> keyValues,
            List<String> projectionColumns
    ) {
    }

    public record DbExistsResult(
            String environment,
            String databaseAlias,
            DbTableRef table,
            boolean exists,
            long count,
            List<Map<String, Object>> projection,
            List<String> warnings
    ) {
    }

    public record DbFilter(
            String column,
            DbOperator operator,
            List<String> values
    ) {
    }

    public record DbFindColumnsRequest(
            String applicationPattern,
            String tableNamePattern,
            String columnNamePattern,
            String javaFieldNameHint,
            Integer limit
    ) {
    }

    public record DbFindRelationshipsRequest(
            List<DbTableRef> tables,
            Integer depth,
            Boolean includeInferred
    ) {
    }

    public record DbFindTablesRequest(
            String applicationPattern,
            String tableNamePattern,
            String entityOrKeywordHint,
            Integer limit
    ) {
    }

    public record DbForeignKeyDescription(
            String constraintName,
            DbTableRef sourceTable,
            List<String> sourceColumns,
            DbTableRef targetTable,
            List<String> targetColumns
    ) {
    }

    public record DbGroupCountRequest(
            DbTableRef table,
            List<String> groupByColumns,
            List<DbFilter> filters,
            Integer limit
    ) {
    }

    public record DbGroupCountResult(
            String environment,
            String databaseAlias,
            DbTableRef table,
            List<Map<String, Object>> groups,
            boolean truncated,
            List<String> warnings
    ) {
    }

    public record DbIndexDescription(
            String indexName,
            boolean unique,
            List<String> columns
    ) {
    }

    public record DbJoinCondition(
            DbColumnRef left,
            DbColumnRef right,
            JoinType type
    ) {
    }

    public record DbJoinCountRequest(
            List<DbTableRef> tables,
            List<DbJoinCondition> joins,
            List<DbQualifiedFilter> filters
    ) {
    }

    public record DbJoinCountResult(
            String environment,
            String databaseAlias,
            long count,
            List<String> warnings
    ) {
    }

    public record DbJoinSampleRequest(
            List<DbTableRef> tables,
            List<DbJoinCondition> joins,
            List<DbProjectedColumn> columns,
            List<DbQualifiedFilter> filters,
            Integer limit
    ) {
    }

    public record DbJoinSampleResult(
            String environment,
            String databaseAlias,
            List<Map<String, Object>> rows,
            boolean truncated,
            List<String> warnings
    ) {
    }

    public record DbKeyValue(
            String column,
            String value
    ) {
    }

    public record DbMappingComparisonRequest(
            DbTableRef actualTable,
            List<ExpectedColumn> expectedColumns,
            List<ExpectedRelationship> expectedRelationships
    ) {
    }

    public record DbMappingComparisonResult(
            String environment,
            String databaseAlias,
            DbTableRef actualTable,
            List<String> confirmedMatches,
            List<String> mismatches,
            List<String> warnings
    ) {
    }

    public record DbOrderBy(
            String column,
            SortDirection direction
    ) {
    }

    public record DbOrphanCheckResult(
            String environment,
            String databaseAlias,
            DbTableRef childTable,
            String childColumn,
            DbTableRef parentTable,
            String parentColumn,
            long orphanCount,
            List<Map<String, Object>> sampleRows,
            boolean truncated,
            List<String> warnings
    ) {
    }

    public record DbProjectedColumn(
            DbColumnRef column,
            String alias
    ) {
    }

    public record DbQualifiedFilter(
            DbColumnRef column,
            DbOperator operator,
            List<String> values
    ) {
    }

    public record DbReadonlySqlRequest(
            String sql,
            String reason,
            Integer maxRows
    ) {
    }

    public record DbReadonlySqlResult(
            String environment,
            String databaseAlias,
            String reason,
            List<Map<String, Object>> rows,
            boolean truncated,
            List<String> warnings
    ) {
    }

    public record DbRelationshipHint(
            DbTableRef sourceTable,
            String sourceColumn,
            DbTableRef targetTable,
            String targetColumn,
            String evidence,
            boolean declared
    ) {
    }

    public record DbRelationshipsResult(
            String environment,
            String databaseAlias,
            List<DbRelationshipHint> relationships,
            List<String> warnings
    ) {
    }

    public record DbSampleRowsRequest(
            DbTableRef table,
            List<String> columns,
            List<DbFilter> filters,
            List<DbOrderBy> orderBy,
            Integer limit
    ) {
    }

    public record DbSampleRowsResult(
            String environment,
            String databaseAlias,
            DbTableRef table,
            List<String> columns,
            List<Map<String, Object>> rows,
            boolean truncated,
            List<String> warnings
    ) {
    }

    public record DbScopeResult(
            String environment,
            String databaseAlias,
            String description,
            List<DbApplicationScopeInfo> applications,
            List<String> allowedSchemas,
            List<String> safetyRules,
            List<String> warnings
    ) {
    }

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

    public record DbTableRef(
            String schema,
            String tableName
    ) {
    }

    public record DbTableSearchResult(
            String environment,
            String databaseAlias,
            String resolvedApplication,
            List<String> resolvedSchemas,
            List<DbTableCandidate> candidates,
            boolean truncated,
            List<String> warnings
    ) {
    }

    public record DbCapabilityScope(
            String correlationId,
            String environment,
            String analysisRunId,
            String copilotSessionId,
            String toolCallId,
            String toolName
    ) {
    }

    public record ExpectedColumn(
            String javaField,
            String expectedColumn,
            String expectedSqlType,
            Boolean nullable,
            Boolean id
    ) {
    }

    public record ExpectedRelationship(
            String javaField,
            String expectedJoinColumn,
            String expectedTargetTable,
            String expectedTargetColumn
    ) {
    }
}
