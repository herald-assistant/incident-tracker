package pl.mkn.incidenttracker.analysis.adapter.database;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.incidenttracker.analysis.mcp.database.DbOperator;
import pl.mkn.incidenttracker.analysis.mcp.database.JoinType;
import pl.mkn.incidenttracker.analysis.mcp.database.SortDirection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolDtos.*;

class DatabaseToolServiceTest {

    @Test
    void shouldRankTableCandidatesAndPopulateMatchedBecause() {
        var service = service();
        var resolvedScope = new ResolvedDatabaseApplicationScope(
                "zt01",
                "oracle",
                "orders-service",
                List.of("ORDERS_APP"),
                List.of(),
                List.of("applicationNamePattern matched orders-service -> ORDERS_APP"),
                List.of()
        );
        var tableMetadata = mock(DatabaseMetadataClient.class);
        when(tableMetadata.camelToSnakeUpper("OrderCorrelationId")).thenReturn("ORDER_CORRELATION_ID");
        when(tableMetadata.findTables("zt01", List.of("ORDERS_APP"), "order", 31)).thenReturn(List.of(
                new TableMetadata("ORDERS_APP", "CUSTOMER", "TABLE", "Customer entity", 5, List.of("ID"), 0, 0, List.of("ID", "CUSTOMER_ID")),
                new TableMetadata("ORDERS_APP", "ORDER_EVENT", "TABLE", "Order entity outbox", 8, List.of("ID"), 1, 0, List.of("ID", "ORDER_ID", "CORRELATION_ID"))
        ));
        when(tableMetadata.inferLikelyKeyColumns(any())).thenAnswer(invocation -> {
            var table = invocation.getArgument(0, TableMetadata.class);
            return "ORDER_EVENT".equals(table.tableName())
                    ? List.of("ID", "ORDER_ID", "CORRELATION_ID")
                    : List.of("ID", "CUSTOMER_ID");
        });

        var resolver = mock(DatabaseApplicationScopeResolver.class);
        when(resolver.resolveDiscoveryScope("zt01", "orders-service")).thenReturn(resolvedScope);
        when(resolver.requiredEnvironment("zt01")).thenReturn(environment("oracle"));

        service = new DatabaseToolService(
                properties(),
                resolver,
                tableMetadata,
                mock(DatabaseReadOnlyQueryClient.class),
                mock(DatabaseSqlGuard.class),
                new DatabaseResultMasker(),
                new DatabaseResultLimiter(properties())
        );

        var result = service.findTables(
                scope(),
                new DbFindTablesRequest("orders-service", "order", "OrderCorrelationId", 30)
        );

        assertEquals("ORDER_EVENT", result.candidates().get(0).tableName());
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("applicationNamePattern matched")));
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("table name matched ORDER")));
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("entityOrKeywordHint matched")));
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("column CORRELATION_ID exists")));
    }

    @Test
    void shouldUseCamelCaseHintWhenRankingColumns() {
        var metadata = mock(DatabaseMetadataClient.class);
        when(metadata.camelToSnakeUpper("correlationId")).thenReturn("CORRELATION_ID");
        when(metadata.findColumns("zt01", List.of("ORDERS_APP"), "order_event", "CORRELATION_ID", 51)).thenReturn(List.of(
                new ColumnMetadata("ORDERS_APP", "ORDER_EVENT", "STATUS", "VARCHAR2", 32, null, null, false, "state"),
                new ColumnMetadata("ORDERS_APP", "ORDER_EVENT", "CORRELATION_ID", "VARCHAR2", 64, null, null, false, "incident correlation id")
        ));

        var resolver = mock(DatabaseApplicationScopeResolver.class);
        when(resolver.resolveDiscoveryScope("zt01", "orders-service")).thenReturn(new ResolvedDatabaseApplicationScope(
                "zt01",
                "oracle",
                "orders-service",
                List.of("ORDERS_APP"),
                List.of(),
                List.of("applicationNamePattern matched orders-service -> ORDERS_APP"),
                List.of()
        ));
        when(resolver.requiredEnvironment("zt01")).thenReturn(environment("oracle"));

        var service = new DatabaseToolService(
                properties(),
                resolver,
                metadata,
                mock(DatabaseReadOnlyQueryClient.class),
                mock(DatabaseSqlGuard.class),
                new DatabaseResultMasker(),
                new DatabaseResultLimiter(properties())
        );

        var result = service.findColumns(
                scope(),
                new DbFindColumnsRequest("orders-service", "order_event", null, "correlationId", 50)
        );

        assertEquals("CORRELATION_ID", result.candidates().get(0).columnName());
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("javaFieldNameHint matched CORRELATION_ID")));
    }

    @Test
    void shouldBuildCountRowsSqlWithBindParameters() {
        var guard = baseGuard();
        var queryClient = mock(DatabaseReadOnlyQueryClient.class);
        when(queryClient.queryForCount(any(), anyString(), anyMap())).thenReturn(3L);

        var service = new DatabaseToolService(
                properties(),
                baseResolver(),
                mock(DatabaseMetadataClient.class),
                queryClient,
                guard,
                new DatabaseResultMasker(),
                new DatabaseResultLimiter(properties())
        );

        var result = service.countRows(
                scope(),
                new DbCountRowsRequest(
                        new DbTableRef("orders_app", "order_event"),
                        List.of(
                                new DbFilter("correlation_id", DbOperator.EQ, List.of("corr-123")),
                                new DbFilter("status", DbOperator.STARTS_WITH, List.of("ACT"))
                        )
                )
        );

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryClient).queryForCount(any(), sqlCaptor.capture(), paramsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("SELECT COUNT(*) FROM ORDERS_APP.ORDER_EVENT t WHERE t.CORRELATION_ID = :p0 AND UPPER(t.STATUS) LIKE UPPER(:p1)"));
        assertEquals("corr-123", paramsCaptor.getValue().get("p0"));
        assertEquals("ACT%", paramsCaptor.getValue().get("p1"));
        assertEquals(3L, result.count());
    }

    @Test
    void shouldBuildGroupCountSqlTemplate() {
        var guard = baseGuard();
        var queryClient = mock(DatabaseReadOnlyQueryClient.class);
        when(queryClient.queryForRows(any(), anyString(), anyMap())).thenReturn(List.of(Map.of("STATUS", "ACTIVE", "ROW_COUNT", 2)));

        var service = new DatabaseToolService(
                properties(),
                baseResolver(),
                mock(DatabaseMetadataClient.class),
                queryClient,
                guard,
                new DatabaseResultMasker(),
                new DatabaseResultLimiter(properties())
        );

        var result = service.groupCount(
                scope(),
                new DbGroupCountRequest(
                        new DbTableRef("orders_app", "order_event"),
                        List.of("status"),
                        List.of(new DbFilter("tenant_id", DbOperator.EQ, List.of("TENANT_A"))),
                        5
                )
        );

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryClient).queryForRows(any(), sqlCaptor.capture(), anyMap());

        assertTrue(sqlCaptor.getValue().contains("SELECT t.STATUS, COUNT(*) AS ROW_COUNT"));
        assertTrue(sqlCaptor.getValue().contains("GROUP BY t.STATUS"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY ROW_COUNT DESC"));
        assertTrue(sqlCaptor.getValue().contains("FETCH FIRST 5 ROWS ONLY"));
        assertEquals(1, result.groups().size());
    }

    @Test
    void shouldBuildOrphanQueryShape() {
        var metadata = mock(DatabaseMetadataClient.class);
        when(metadata.describeTable("zt01", "ORDERS_APP", "ORDER_EVENT")).thenReturn(new TableDescriptionMetadata(
                "ORDERS_APP",
                "ORDER_EVENT",
                "TABLE",
                "order event",
                List.of(new ColumnDefinition("ID", "NUMBER", null, 19, 0, false, null, null)),
                List.of("ID"),
                List.of(),
                List.of(),
                List.of(),
                java.util.Set.of()
        ));

        var queryClient = mock(DatabaseReadOnlyQueryClient.class);
        when(queryClient.queryForCount(any(), anyString(), anyMap())).thenReturn(2L);
        var row = new LinkedHashMap<String, Object>();
        row.put("ORDER_ID", "ORD-1");
        row.put("ID", 10);
        when(queryClient.queryForRows(any(), anyString(), anyMap())).thenReturn(List.of(row));

        var service = new DatabaseToolService(
                properties(),
                baseResolver(),
                metadata,
                queryClient,
                baseGuard(),
                new DatabaseResultMasker(),
                new DatabaseResultLimiter(properties())
        );

        var result = service.checkOrphans(
                scope(),
                new DbCheckOrphansRequest(
                        new DbTableRef("orders_app", "order_event"),
                        "order_id",
                        new DbTableRef("orders_app", "order"),
                        "id",
                        List.of(new DbFilter("tenant_id", DbOperator.EQ, List.of("TENANT_A"))),
                        5
                )
        );

        verify(queryClient).queryForCount(any(), org.mockito.ArgumentMatchers.contains("LEFT JOIN ORDERS_APP.ORDER p"), anyMap());
        verify(queryClient).queryForRows(any(), org.mockito.ArgumentMatchers.contains("FETCH FIRST 5 ROWS ONLY"), anyMap());
        assertEquals(2L, result.orphanCount());
        assertEquals(1, result.sampleRows().size());
    }

    @Test
    void shouldAliasTablesInJoinCountAndBindParameters() {
        var queryClient = mock(DatabaseReadOnlyQueryClient.class);
        when(queryClient.queryForCount(any(), anyString(), anyMap())).thenReturn(7L);

        var service = new DatabaseToolService(
                properties(),
                baseResolver(),
                mock(DatabaseMetadataClient.class),
                queryClient,
                baseGuard(),
                new DatabaseResultMasker(),
                new DatabaseResultLimiter(properties())
        );

        var result = service.joinCount(
                scope(),
                new DbJoinCountRequest(
                        List.of(
                                new DbTableRef("orders_app", "order_event"),
                                new DbTableRef("common_dict", "order_status")
                        ),
                        List.of(new DbJoinCondition(
                                new DbColumnRef(new DbTableRef("orders_app", "order_event"), "status_id"),
                                new DbColumnRef(new DbTableRef("common_dict", "order_status"), "id"),
                                JoinType.INNER
                        )),
                        List.of(new DbQualifiedFilter(
                                new DbColumnRef(new DbTableRef("common_dict", "order_status"), "status"),
                                DbOperator.EQ,
                                List.of("ACTIVE")
                        ))
                )
        );

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryClient).queryForCount(any(), sqlCaptor.capture(), anyMap());

        assertTrue(sqlCaptor.getValue().contains("FROM ORDERS_APP.ORDER_EVENT t0 INNER JOIN COMMON_DICT.ORDER_STATUS t1 ON t0.STATUS_ID = t1.ID"));
        assertTrue(sqlCaptor.getValue().contains("WHERE t1.STATUS = :p0"));
        assertEquals(7L, result.count());
    }

    private DatabaseToolService service() {
        return new DatabaseToolService(
                properties(),
                baseResolver(),
                mock(DatabaseMetadataClient.class),
                mock(DatabaseReadOnlyQueryClient.class),
                baseGuard(),
                new DatabaseResultMasker(),
                new DatabaseResultLimiter(properties())
        );
    }

    private DatabaseToolProperties properties() {
        var properties = new DatabaseToolProperties();
        properties.setMaxRows(50);
        properties.setMaxColumns(40);
        properties.setMaxTablesPerSearch(30);
        properties.setMaxColumnsPerSearch(50);
        properties.setMaxResultCharacters(64_000);
        return properties;
    }

    private DatabaseEnvironmentProperties environment(String alias) {
        var environment = new DatabaseEnvironmentProperties();
        environment.setDatabaseAlias(alias);
        return environment;
    }

    private DatabaseApplicationScopeResolver baseResolver() {
        var resolver = mock(DatabaseApplicationScopeResolver.class);
        when(resolver.requiredEnvironment("zt01")).thenReturn(environment("oracle"));
        return resolver;
    }

    private DatabaseSqlGuard baseGuard() {
        var guard = mock(DatabaseSqlGuard.class);
        when(guard.normalizeTableRef(any(), any())).thenAnswer(invocation -> {
            var table = invocation.getArgument(1, DbTableRef.class);
            return new DbTableRef(table.schema().toUpperCase(), table.tableName().toUpperCase());
        });
        when(guard.validateColumn(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2, String.class).toUpperCase());
        when(guard.validateColumns(any(), any(), anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var columns = (List<String>) invocation.getArgument(2, List.class);
            return columns.stream().map(String::toUpperCase).toList();
        });
        when(guard.normalizeIdentifier(anyString())).thenAnswer(invocation -> invocation.getArgument(0, String.class).toUpperCase());
        return guard;
    }

    private DbToolScope scope() {
        return new DbToolScope("corr-123", "zt01", "run-1", "analysis-run-1", "tool-call-1", "db_test_tool");
    }
}
