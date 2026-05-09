package pl.mkn.incidenttracker.agenttools.database.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.incidenttracker.integrations.database.DbOperator;
import pl.mkn.incidenttracker.integrations.database.JoinType;
import pl.mkn.incidenttracker.integrations.database.SortDirection;
import pl.mkn.incidenttracker.integrations.database.DatabaseToolService;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.integrations.database.DatabaseCapabilityDtos.*;

class DatabaseMcpToolsTest {

    private final DatabaseToolService databaseToolService = mock(DatabaseToolService.class);
    private final DatabaseMcpTools tools = new DatabaseMcpTools(databaseToolService);

    @Test
    void shouldDelegateGetScopeWithSessionBoundScope() {
        when(databaseToolService.getScope(argThat(this::scopeMatches))).thenReturn(scopeResult());

        tools.getScope("Sprawdzam zakres dostepu DB.", toolContext());

        verify(databaseToolService).getScope(argThat(this::scopeMatches));
    }

    @Test
    void shouldDelegateFindTablesWithApplicationPattern() {
        when(databaseToolService.findTables(argThat(this::scopeMatches), eq(new DbFindTablesRequest("crm-service", "CUSTOMER_PROFILE", "CustomerProfileEntity", 10))))
                .thenReturn(tableSearchResult());

        tools.findTables("crm-service", "CUSTOMER_PROFILE", "CustomerProfileEntity", 10, "Szukam tabeli klientow.", toolContext());

        verify(databaseToolService).findTables(
                argThat(this::scopeMatches),
                eq(new DbFindTablesRequest("crm-service", "CUSTOMER_PROFILE", "CustomerProfileEntity", 10))
        );
    }

    @Test
    void shouldDelegateFindColumnsWithApplicationPattern() {
        when(databaseToolService.findColumns(argThat(this::scopeMatches), eq(new DbFindColumnsRequest("crm-service", "CUSTOMER_INTERACTION", "STATUS", "statusCode", 10))))
                .thenReturn(columnSearchResult());

        tools.findColumns("crm-service", "CUSTOMER_INTERACTION", "STATUS", "statusCode", 10, "Szukam kolumny statusu.", toolContext());

        verify(databaseToolService).findColumns(
                argThat(this::scopeMatches),
                eq(new DbFindColumnsRequest("crm-service", "CUSTOMER_INTERACTION", "STATUS", "statusCode", 10))
        );
    }

    @Test
    void shouldDelegateDescribeTable() {
        var table = new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION");
        when(databaseToolService.describeTable(argThat(this::scopeMatches), eq(new DbDescribeTableRequest(table))))
                .thenReturn(tableDescription());

        tools.describeTable(table, "Sprawdzam strukture tabeli.", toolContext());

        verify(databaseToolService).describeTable(
                argThat(this::scopeMatches),
                eq(new DbDescribeTableRequest(table))
        );
    }

    @Test
    void shouldDelegateExistsByKey() {
        var table = new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION");
        var keyValues = List.of(new DbKeyValue("ID", "123"));
        var projectionColumns = List.of("ID", "STATUS");
        when(databaseToolService.existsByKey(argThat(this::scopeMatches), eq(new DbExistsByKeyRequest(table, keyValues, projectionColumns))))
                .thenReturn(existsResult());

        tools.existsByKey(table, keyValues, projectionColumns, "Sprawdzam, czy rekord istnieje.", toolContext());

        verify(databaseToolService).existsByKey(
                argThat(this::scopeMatches),
                eq(new DbExistsByKeyRequest(table, keyValues, projectionColumns))
        );
    }

    @Test
    void shouldDelegateCountRows() {
        var table = new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION");
        var filters = List.of(new DbFilter("STATUS", DbOperator.EQ, List.of("ACTIVE")));
        when(databaseToolService.countRows(argThat(this::scopeMatches), eq(new DbCountRowsRequest(table, filters))))
                .thenReturn(countResult());

        tools.countRows(table, filters, "Licze rekordy dla filtra statusu.", toolContext());

        verify(databaseToolService).countRows(
                argThat(this::scopeMatches),
                eq(new DbCountRowsRequest(table, filters))
        );
    }

    @Test
    void shouldDelegateGroupCount() {
        var table = new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION");
        var filters = List.of(new DbFilter("TENANT_ID", DbOperator.EQ, List.of("TENANT_A")));
        when(databaseToolService.groupCount(argThat(this::scopeMatches), eq(new DbGroupCountRequest(table, List.of("STATUS"), filters, 5))))
                .thenReturn(groupCountResult());

        tools.groupCount(table, List.of("STATUS"), filters, 5, "Sprawdzam rozklad statusow.", toolContext());

        verify(databaseToolService).groupCount(
                argThat(this::scopeMatches),
                eq(new DbGroupCountRequest(table, List.of("STATUS"), filters, 5))
        );
    }

    @Test
    void shouldDelegateSampleRows() {
        var table = new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION");
        var filters = List.of(new DbFilter("STATUS", DbOperator.EQ, List.of("ACTIVE")));
        var orderBy = List.of(new DbOrderBy("UPDATED_AT", SortDirection.DESC));
        when(databaseToolService.sampleRows(argThat(this::scopeMatches), eq(new DbSampleRowsRequest(table, List.of("ID"), filters, orderBy, 5))))
                .thenReturn(sampleRowsResult());

        tools.sampleRows(table, List.of("ID"), filters, orderBy, 5, "Pobieram mala probke techniczna.", toolContext());

        verify(databaseToolService).sampleRows(
                argThat(this::scopeMatches),
                eq(new DbSampleRowsRequest(table, List.of("ID"), filters, orderBy, 5))
        );
    }

    @Test
    void shouldDelegateCheckOrphans() {
        var childTable = new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION");
        var parentTable = new DbTableRef("CRM_APP", "CUSTOMER_PROFILE");
        var filters = List.of(new DbFilter("STATUS", DbOperator.EQ, List.of("FAILED")));
        when(databaseToolService.checkOrphans(
                argThat(this::scopeMatches),
                eq(new DbCheckOrphansRequest(childTable, "CUSTOMER_ID", parentTable, "ID", filters, 5))
        )).thenReturn(orphanCheckResult());

        tools.checkOrphans(childTable, "CUSTOMER_ID", parentTable, "ID", filters, 5, "Sprawdzam osierocone relacje.", toolContext());

        verify(databaseToolService).checkOrphans(
                argThat(this::scopeMatches),
                eq(new DbCheckOrphansRequest(childTable, "CUSTOMER_ID", parentTable, "ID", filters, 5))
        );
    }

    @Test
    void shouldDelegateFindRelationships() {
        var tables = List.of(new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"));
        when(databaseToolService.findRelationships(argThat(this::scopeMatches), eq(new DbFindRelationshipsRequest(tables, 2, true))))
                .thenReturn(relationshipsResult());

        tools.findRelationships(tables, 2, true, "Sprawdzam relacje tabel.", toolContext());

        verify(databaseToolService).findRelationships(
                argThat(this::scopeMatches),
                eq(new DbFindRelationshipsRequest(tables, 2, true))
        );
    }

    @Test
    void shouldDelegateJoinCount() {
        var tables = List.of(
                new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"),
                new DbTableRef("COMMON_DICT", "CUSTOMER_STATUS")
        );
        var joins = List.of(new DbJoinCondition(
                new DbColumnRef(new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), "STATUS_ID"),
                new DbColumnRef(new DbTableRef("COMMON_DICT", "CUSTOMER_STATUS"), "ID"),
                JoinType.INNER
        ));
        var filters = List.of(new DbQualifiedFilter(
                new DbColumnRef(new DbTableRef("COMMON_DICT", "CUSTOMER_STATUS"), "STATUS"),
                DbOperator.EQ,
                List.of("ACTIVE")
        ));
        when(databaseToolService.joinCount(argThat(this::scopeMatches), eq(new DbJoinCountRequest(tables, joins, filters))))
                .thenReturn(joinCountResult());

        tools.joinCount(tables, joins, filters, "Licze wynik joina.", toolContext());

        verify(databaseToolService).joinCount(
                argThat(this::scopeMatches),
                eq(new DbJoinCountRequest(tables, joins, filters))
        );
    }

    @Test
    void shouldDelegateJoinSample() {
        var tables = List.of(
                new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"),
                new DbTableRef("COMMON_DICT", "CUSTOMER_STATUS")
        );
        var joins = List.of(new DbJoinCondition(
                new DbColumnRef(new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), "STATUS_ID"),
                new DbColumnRef(new DbTableRef("COMMON_DICT", "CUSTOMER_STATUS"), "ID"),
                JoinType.INNER
        ));
        var columns = List.of(new DbProjectedColumn(
                new DbColumnRef(new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), "ID"),
                "CUSTOMER_ID"
        ));
        var filters = List.of(new DbQualifiedFilter(
                new DbColumnRef(new DbTableRef("COMMON_DICT", "CUSTOMER_STATUS"), "STATUS"),
                DbOperator.EQ,
                List.of("ACTIVE")
        ));
        when(databaseToolService.joinSample(argThat(this::scopeMatches), eq(new DbJoinSampleRequest(tables, joins, columns, filters, 5))))
                .thenReturn(joinSampleResult());

        tools.joinSample(tables, joins, columns, filters, 5, "Pobieram probke joina.", toolContext());

        verify(databaseToolService).joinSample(
                argThat(this::scopeMatches),
                eq(new DbJoinSampleRequest(tables, joins, columns, filters, 5))
        );
    }

    @Test
    void shouldDelegateCompareTableToExpectedMapping() {
        var table = new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION");
        var expectedColumns = List.of(new ExpectedColumn("id", "ID", "NUMBER", false, true));
        var expectedRelationships = List.of(new ExpectedRelationship("customer_profile", "CUSTOMER_ID", "CUSTOMER_PROFILE", "ID"));
        when(databaseToolService.compareTableToExpectedMapping(
                argThat(this::scopeMatches),
                eq(new DbMappingComparisonRequest(table, expectedColumns, expectedRelationships))
        )).thenReturn(mappingComparisonResult());

        tools.compareTableToExpectedMapping(table, expectedColumns, expectedRelationships, "Porownuje tabele z mapowaniem.", toolContext());

        verify(databaseToolService).compareTableToExpectedMapping(
                argThat(this::scopeMatches),
                eq(new DbMappingComparisonRequest(table, expectedColumns, expectedRelationships))
        );
    }

    @Test
    void shouldDelegateExecuteReadonlySql() {
        when(databaseToolService.executeReadonlySql(
                argThat(this::scopeMatches),
                eq(new DbReadonlySqlRequest("SELECT 1 FROM dual", "Sprawdzam wynik prostego SELECT.", 5))
        )).thenReturn(readonlySqlResult());

        tools.executeReadonlySql("SELECT 1 FROM dual", "Sprawdzam wynik prostego SELECT.", 5, toolContext());

        verify(databaseToolService).executeReadonlySql(
                argThat(this::scopeMatches),
                eq(new DbReadonlySqlRequest("SELECT 1 FROM dual", "Sprawdzam wynik prostego SELECT.", 5))
        );
    }

    private boolean scopeMatches(DbCapabilityScope scope) {
        return scope != null
                && "corr-123".equals(scope.correlationId())
                && "sandbox-a".equals(scope.environment())
                && "run-1".equals(scope.analysisRunId())
                && "analysis-run-1".equals(scope.copilotSessionId())
                && "tool-call-1".equals(scope.toolCallId())
                && "db_test_tool".equals(scope.toolName());
    }

    private ToolContext toolContext() {
        var context = new LinkedHashMap<String, Object>();
        context.put(AgentToolContextKeys.CORRELATION_ID, "corr-123");
        context.put(AgentToolContextKeys.ENVIRONMENT, "sandbox-a");
        context.put(AgentToolContextKeys.ANALYSIS_RUN_ID, "run-1");
        context.put(AgentToolContextKeys.COPILOT_SESSION_ID, "analysis-run-1");
        context.put(AgentToolContextKeys.TOOL_CALL_ID, "tool-call-1");
        context.put(AgentToolContextKeys.TOOL_NAME, "db_test_tool");
        return new ToolContext(context);
    }

    private DbScopeResult scopeResult() {
        return new DbScopeResult("sandbox-a", "oracle", "desc", List.of(), List.of("CRM_APP"), List.of(), List.of());
    }

    private DbTableSearchResult tableSearchResult() {
        return new DbTableSearchResult("sandbox-a", "oracle", "crm-service", List.of("CRM_APP"), List.of(), false, List.of());
    }

    private DbColumnSearchResult columnSearchResult() {
        return new DbColumnSearchResult("sandbox-a", "oracle", "crm-service", List.of("CRM_APP"), List.of(), false, List.of());
    }

    private DbTableDescription tableDescription() {
        return new DbTableDescription("sandbox-a", "oracle", "CRM_APP", "CUSTOMER_INTERACTION", "TABLE", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private DbExistsResult existsResult() {
        return new DbExistsResult("sandbox-a", "oracle", new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), true, 1, List.of(), List.of());
    }

    private DbCountResult countResult() {
        return new DbCountResult("sandbox-a", "oracle", new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), 1, List.of(), List.of());
    }

    private DbGroupCountResult groupCountResult() {
        return new DbGroupCountResult("sandbox-a", "oracle", new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), List.of(), false, List.of());
    }

    private DbSampleRowsResult sampleRowsResult() {
        return new DbSampleRowsResult("sandbox-a", "oracle", new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), List.of("ID"), List.of(), false, List.of());
    }

    private DbOrphanCheckResult orphanCheckResult() {
        return new DbOrphanCheckResult("sandbox-a", "oracle", new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), "CUSTOMER_ID", new DbTableRef("CRM_APP", "CUSTOMER_PROFILE"), "ID", 1, List.of(), false, List.of());
    }

    private DbRelationshipsResult relationshipsResult() {
        return new DbRelationshipsResult("sandbox-a", "oracle", List.of(), List.of());
    }

    private DbJoinCountResult joinCountResult() {
        return new DbJoinCountResult("sandbox-a", "oracle", 1, List.of());
    }

    private DbJoinSampleResult joinSampleResult() {
        return new DbJoinSampleResult("sandbox-a", "oracle", List.of(), false, List.of());
    }

    private DbMappingComparisonResult mappingComparisonResult() {
        return new DbMappingComparisonResult("sandbox-a", "oracle", new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"), List.of(), List.of(), List.of());
    }

    private DbReadonlySqlResult readonlySqlResult() {
        return new DbReadonlySqlResult("sandbox-a", "oracle", "Sprawdzam wynik prostego SELECT.", List.of(Map.of("VALUE", 1)), false, List.of());
    }
}
