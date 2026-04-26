package pl.mkn.incidenttracker.analysis.mcp.database;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.incidenttracker.analysis.adapter.database.DatabaseToolService;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolDtos.*;

class DatabaseMcpToolsTest {

    private final DatabaseToolService databaseToolService = mock(DatabaseToolService.class);
    private final DatabaseMcpTools tools = new DatabaseMcpTools(databaseToolService);

    @Test
    void shouldDelegateGetScopeWithSessionBoundScope() {
        when(databaseToolService.getScope(argThat(this::scopeMatches))).thenReturn(scopeResult());

        tools.getScope(toolContext());

        verify(databaseToolService).getScope(argThat(this::scopeMatches));
    }

    @Test
    void shouldDelegateFindTablesWithApplicationNamePattern() {
        when(databaseToolService.findTables(argThat(this::scopeMatches), eq(new DbFindTablesRequest("orders-service", "ORDER", "OrderEntity", 10))))
                .thenReturn(tableSearchResult());

        tools.findTables("orders-service", "ORDER", "OrderEntity", 10, toolContext());

        verify(databaseToolService).findTables(
                argThat(this::scopeMatches),
                eq(new DbFindTablesRequest("orders-service", "ORDER", "OrderEntity", 10))
        );
    }

    @Test
    void shouldDelegateFindColumnsWithApplicationNamePattern() {
        when(databaseToolService.findColumns(argThat(this::scopeMatches), eq(new DbFindColumnsRequest("orders-service", "ORDER_EVENT", "STATUS", "statusCode", 10))))
                .thenReturn(columnSearchResult());

        tools.findColumns("orders-service", "ORDER_EVENT", "STATUS", "statusCode", 10, toolContext());

        verify(databaseToolService).findColumns(
                argThat(this::scopeMatches),
                eq(new DbFindColumnsRequest("orders-service", "ORDER_EVENT", "STATUS", "statusCode", 10))
        );
    }

    @Test
    void shouldDelegateDescribeTable() {
        var table = new DbTableRef("ORDERS_APP", "ORDER_EVENT");
        when(databaseToolService.describeTable(argThat(this::scopeMatches), eq(new DbDescribeTableRequest(table))))
                .thenReturn(tableDescription());

        tools.describeTable(table, toolContext());

        verify(databaseToolService).describeTable(
                argThat(this::scopeMatches),
                eq(new DbDescribeTableRequest(table))
        );
    }

    @Test
    void shouldDelegateExistsByKey() {
        var table = new DbTableRef("ORDERS_APP", "ORDER_EVENT");
        var keyValues = List.of(new DbKeyValue("ID", "123"));
        var projectionColumns = List.of("ID", "STATUS");
        when(databaseToolService.existsByKey(argThat(this::scopeMatches), eq(new DbExistsByKeyRequest(table, keyValues, projectionColumns))))
                .thenReturn(existsResult());

        tools.existsByKey(table, keyValues, projectionColumns, toolContext());

        verify(databaseToolService).existsByKey(
                argThat(this::scopeMatches),
                eq(new DbExistsByKeyRequest(table, keyValues, projectionColumns))
        );
    }

    @Test
    void shouldDelegateCountRows() {
        var table = new DbTableRef("ORDERS_APP", "ORDER_EVENT");
        var filters = List.of(new DbFilter("STATUS", DbOperator.EQ, List.of("ACTIVE")));
        when(databaseToolService.countRows(argThat(this::scopeMatches), eq(new DbCountRowsRequest(table, filters))))
                .thenReturn(countResult());

        tools.countRows(table, filters, toolContext());

        verify(databaseToolService).countRows(
                argThat(this::scopeMatches),
                eq(new DbCountRowsRequest(table, filters))
        );
    }

    @Test
    void shouldDelegateGroupCount() {
        var table = new DbTableRef("ORDERS_APP", "ORDER_EVENT");
        var filters = List.of(new DbFilter("TENANT_ID", DbOperator.EQ, List.of("TENANT_A")));
        when(databaseToolService.groupCount(argThat(this::scopeMatches), eq(new DbGroupCountRequest(table, List.of("STATUS"), filters, 5))))
                .thenReturn(groupCountResult());

        tools.groupCount(table, List.of("STATUS"), filters, 5, toolContext());

        verify(databaseToolService).groupCount(
                argThat(this::scopeMatches),
                eq(new DbGroupCountRequest(table, List.of("STATUS"), filters, 5))
        );
    }

    @Test
    void shouldDelegateSampleRows() {
        var table = new DbTableRef("ORDERS_APP", "ORDER_EVENT");
        var filters = List.of(new DbFilter("STATUS", DbOperator.EQ, List.of("ACTIVE")));
        var orderBy = List.of(new DbOrderBy("UPDATED_AT", SortDirection.DESC));
        when(databaseToolService.sampleRows(argThat(this::scopeMatches), eq(new DbSampleRowsRequest(table, List.of("ID"), filters, orderBy, 5))))
                .thenReturn(sampleRowsResult());

        tools.sampleRows(table, List.of("ID"), filters, orderBy, 5, toolContext());

        verify(databaseToolService).sampleRows(
                argThat(this::scopeMatches),
                eq(new DbSampleRowsRequest(table, List.of("ID"), filters, orderBy, 5))
        );
    }

    @Test
    void shouldDelegateCheckOrphans() {
        var childTable = new DbTableRef("ORDERS_APP", "ORDER_EVENT");
        var parentTable = new DbTableRef("ORDERS_APP", "ORDER");
        var filters = List.of(new DbFilter("STATUS", DbOperator.EQ, List.of("FAILED")));
        when(databaseToolService.checkOrphans(
                argThat(this::scopeMatches),
                eq(new DbCheckOrphansRequest(childTable, "ORDER_ID", parentTable, "ID", filters, 5))
        )).thenReturn(orphanCheckResult());

        tools.checkOrphans(childTable, "ORDER_ID", parentTable, "ID", filters, 5, toolContext());

        verify(databaseToolService).checkOrphans(
                argThat(this::scopeMatches),
                eq(new DbCheckOrphansRequest(childTable, "ORDER_ID", parentTable, "ID", filters, 5))
        );
    }

    @Test
    void shouldDelegateFindRelationships() {
        var tables = List.of(new DbTableRef("ORDERS_APP", "ORDER_EVENT"));
        when(databaseToolService.findRelationships(argThat(this::scopeMatches), eq(new DbFindRelationshipsRequest(tables, 2, true))))
                .thenReturn(relationshipsResult());

        tools.findRelationships(tables, 2, true, toolContext());

        verify(databaseToolService).findRelationships(
                argThat(this::scopeMatches),
                eq(new DbFindRelationshipsRequest(tables, 2, true))
        );
    }

    @Test
    void shouldDelegateJoinCount() {
        var tables = List.of(
                new DbTableRef("ORDERS_APP", "ORDER_EVENT"),
                new DbTableRef("COMMON_DICT", "ORDER_STATUS")
        );
        var joins = List.of(new DbJoinCondition(
                new DbColumnRef(new DbTableRef("ORDERS_APP", "ORDER_EVENT"), "STATUS_ID"),
                new DbColumnRef(new DbTableRef("COMMON_DICT", "ORDER_STATUS"), "ID"),
                JoinType.INNER
        ));
        var filters = List.of(new DbQualifiedFilter(
                new DbColumnRef(new DbTableRef("COMMON_DICT", "ORDER_STATUS"), "STATUS"),
                DbOperator.EQ,
                List.of("ACTIVE")
        ));
        when(databaseToolService.joinCount(argThat(this::scopeMatches), eq(new DbJoinCountRequest(tables, joins, filters))))
                .thenReturn(joinCountResult());

        tools.joinCount(tables, joins, filters, toolContext());

        verify(databaseToolService).joinCount(
                argThat(this::scopeMatches),
                eq(new DbJoinCountRequest(tables, joins, filters))
        );
    }

    @Test
    void shouldDelegateJoinSample() {
        var tables = List.of(
                new DbTableRef("ORDERS_APP", "ORDER_EVENT"),
                new DbTableRef("COMMON_DICT", "ORDER_STATUS")
        );
        var joins = List.of(new DbJoinCondition(
                new DbColumnRef(new DbTableRef("ORDERS_APP", "ORDER_EVENT"), "STATUS_ID"),
                new DbColumnRef(new DbTableRef("COMMON_DICT", "ORDER_STATUS"), "ID"),
                JoinType.INNER
        ));
        var columns = List.of(new DbProjectedColumn(
                new DbColumnRef(new DbTableRef("ORDERS_APP", "ORDER_EVENT"), "ID"),
                "ORDER_ID"
        ));
        var filters = List.of(new DbQualifiedFilter(
                new DbColumnRef(new DbTableRef("COMMON_DICT", "ORDER_STATUS"), "STATUS"),
                DbOperator.EQ,
                List.of("ACTIVE")
        ));
        when(databaseToolService.joinSample(argThat(this::scopeMatches), eq(new DbJoinSampleRequest(tables, joins, columns, filters, 5))))
                .thenReturn(joinSampleResult());

        tools.joinSample(tables, joins, columns, filters, 5, toolContext());

        verify(databaseToolService).joinSample(
                argThat(this::scopeMatches),
                eq(new DbJoinSampleRequest(tables, joins, columns, filters, 5))
        );
    }

    @Test
    void shouldDelegateCompareTableToExpectedMapping() {
        var table = new DbTableRef("ORDERS_APP", "ORDER_EVENT");
        var expectedColumns = List.of(new ExpectedColumn("id", "ID", "NUMBER", false, true));
        var expectedRelationships = List.of(new ExpectedRelationship("order", "ORDER_ID", "ORDER", "ID"));
        when(databaseToolService.compareTableToExpectedMapping(
                argThat(this::scopeMatches),
                eq(new DbMappingComparisonRequest(table, expectedColumns, expectedRelationships))
        )).thenReturn(mappingComparisonResult());

        tools.compareTableToExpectedMapping(table, expectedColumns, expectedRelationships, toolContext());

        verify(databaseToolService).compareTableToExpectedMapping(
                argThat(this::scopeMatches),
                eq(new DbMappingComparisonRequest(table, expectedColumns, expectedRelationships))
        );
    }

    @Test
    void shouldDelegateExecuteReadonlySql() {
        when(databaseToolService.executeReadonlySql(
                argThat(this::scopeMatches),
                eq(new DbReadonlySqlRequest("SELECT 1 FROM dual", "fallback", 5))
        )).thenReturn(readonlySqlResult());

        tools.executeReadonlySql("SELECT 1 FROM dual", "fallback", 5, toolContext());

        verify(databaseToolService).executeReadonlySql(
                argThat(this::scopeMatches),
                eq(new DbReadonlySqlRequest("SELECT 1 FROM dual", "fallback", 5))
        );
    }

    private boolean scopeMatches(DbToolScope scope) {
        return scope != null
                && "corr-123".equals(scope.correlationId())
                && "zt01".equals(scope.environment())
                && "run-1".equals(scope.analysisRunId())
                && "analysis-run-1".equals(scope.copilotSessionId())
                && "tool-call-1".equals(scope.toolCallId())
                && "db_test_tool".equals(scope.toolName());
    }

    private ToolContext toolContext() {
        var context = new LinkedHashMap<String, Object>();
        context.put(CopilotToolContextKeys.CORRELATION_ID, "corr-123");
        context.put(CopilotToolContextKeys.ENVIRONMENT, "zt01");
        context.put(CopilotToolContextKeys.ANALYSIS_RUN_ID, "run-1");
        context.put(CopilotToolContextKeys.COPILOT_SESSION_ID, "analysis-run-1");
        context.put(CopilotToolContextKeys.TOOL_CALL_ID, "tool-call-1");
        context.put(CopilotToolContextKeys.TOOL_NAME, "db_test_tool");
        return new ToolContext(context);
    }

    private DbScopeResult scopeResult() {
        return new DbScopeResult("zt01", "oracle", "desc", List.of(), List.of("ORDERS_APP"), List.of(), List.of());
    }

    private DbTableSearchResult tableSearchResult() {
        return new DbTableSearchResult("zt01", "oracle", "orders-service", List.of("ORDERS_APP"), List.of(), false, List.of());
    }

    private DbColumnSearchResult columnSearchResult() {
        return new DbColumnSearchResult("zt01", "oracle", "orders-service", List.of("ORDERS_APP"), List.of(), false, List.of());
    }

    private DbTableDescription tableDescription() {
        return new DbTableDescription("zt01", "oracle", "ORDERS_APP", "ORDER_EVENT", "TABLE", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private DbExistsResult existsResult() {
        return new DbExistsResult("zt01", "oracle", new DbTableRef("ORDERS_APP", "ORDER_EVENT"), true, 1, List.of(), List.of());
    }

    private DbCountResult countResult() {
        return new DbCountResult("zt01", "oracle", new DbTableRef("ORDERS_APP", "ORDER_EVENT"), 1, List.of(), List.of());
    }

    private DbGroupCountResult groupCountResult() {
        return new DbGroupCountResult("zt01", "oracle", new DbTableRef("ORDERS_APP", "ORDER_EVENT"), List.of(), false, List.of());
    }

    private DbSampleRowsResult sampleRowsResult() {
        return new DbSampleRowsResult("zt01", "oracle", new DbTableRef("ORDERS_APP", "ORDER_EVENT"), List.of("ID"), List.of(), false, List.of());
    }

    private DbOrphanCheckResult orphanCheckResult() {
        return new DbOrphanCheckResult("zt01", "oracle", new DbTableRef("ORDERS_APP", "ORDER_EVENT"), "ORDER_ID", new DbTableRef("ORDERS_APP", "ORDER"), "ID", 1, List.of(), false, List.of());
    }

    private DbRelationshipsResult relationshipsResult() {
        return new DbRelationshipsResult("zt01", "oracle", List.of(), List.of());
    }

    private DbJoinCountResult joinCountResult() {
        return new DbJoinCountResult("zt01", "oracle", 1, List.of());
    }

    private DbJoinSampleResult joinSampleResult() {
        return new DbJoinSampleResult("zt01", "oracle", List.of(), false, List.of());
    }

    private DbMappingComparisonResult mappingComparisonResult() {
        return new DbMappingComparisonResult("zt01", "oracle", new DbTableRef("ORDERS_APP", "ORDER_EVENT"), List.of(), List.of(), List.of());
    }

    private DbReadonlySqlResult readonlySqlResult() {
        return new DbReadonlySqlResult("zt01", "oracle", "fallback", List.of(Map.of("VALUE", 1)), false, List.of());
    }
}
