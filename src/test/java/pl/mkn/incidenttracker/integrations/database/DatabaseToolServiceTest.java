package pl.mkn.incidenttracker.integrations.database;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.incidenttracker.integrations.database.DbOperator;
import pl.mkn.incidenttracker.integrations.database.JoinType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.integrations.database.DatabaseCapabilityDtos.*;

class DatabaseToolServiceTest {

    @Test
    void shouldRankTableCandidatesAndPopulateMatchedBecause() {
        var service = service();
        var resolvedScope = new ResolvedDatabaseApplicationScope(
                "sandbox-a",
                "oracle",
                "crm-service",
                List.of("CRM_APP"),
                List.of(),
                List.of("applicationPattern matched crm-service -> CRM_APP"),
                List.of()
        );
        var tableMetadata = mock(DatabaseMetadataClient.class);
        when(tableMetadata.camelToSnakeUpper("CustomerCorrelationId")).thenReturn("CUSTOMER_CORRELATION_ID");
        when(tableMetadata.findTables("sandbox-a", List.of("CRM_APP"), "customer", 31)).thenReturn(List.of(
                new TableMetadata("CRM_APP", "CUSTOMER", "TABLE", "Customer entity", 5, List.of("ID"), 0, 0, List.of("ID", "CUSTOMER_ID")),
                new TableMetadata("CRM_APP", "CUSTOMER_INTERACTION", "TABLE", "Customer interaction outbox", 8, List.of("ID"), 1, 0, List.of("ID", "CUSTOMER_ID", "CORRELATION_ID"))
        ));
        when(tableMetadata.inferLikelyKeyColumns(any())).thenAnswer(invocation -> {
            var table = invocation.getArgument(0, TableMetadata.class);
            return "CUSTOMER_INTERACTION".equals(table.tableName())
                    ? List.of("ID", "CUSTOMER_ID", "CORRELATION_ID")
                    : List.of("ID", "CUSTOMER_ID");
        });

        var resolver = mock(DatabaseApplicationScopeResolver.class);
        when(resolver.resolveDiscoveryScope("sandbox-a", "crm-service")).thenReturn(resolvedScope);
        when(resolver.requiredEnvironment("sandbox-a")).thenReturn(environment("oracle"));

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
                new DbFindTablesRequest("crm-service", "customer", "CustomerCorrelationId", 30)
        );

        assertEquals("CUSTOMER_INTERACTION", result.candidates().get(0).tableName());
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("applicationPattern matched")));
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("table name matched CUSTOMER")));
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("entityOrKeywordHint matched")));
        assertTrue(result.candidates().get(0).matchedBecause().stream().anyMatch(reason -> reason.contains("column CORRELATION_ID exists")));
    }

    @Test
    void shouldUseCamelCaseHintWhenRankingColumns() {
        var metadata = mock(DatabaseMetadataClient.class);
        when(metadata.camelToSnakeUpper("correlationId")).thenReturn("CORRELATION_ID");
        when(metadata.findColumns("sandbox-a", List.of("CRM_APP"), "customer_interaction", "CORRELATION_ID", 51)).thenReturn(List.of(
                new ColumnMetadata("CRM_APP", "CUSTOMER_INTERACTION", "STATUS", "VARCHAR2", 32, null, null, false, "state"),
                new ColumnMetadata("CRM_APP", "CUSTOMER_INTERACTION", "CORRELATION_ID", "VARCHAR2", 64, null, null, false, "incident correlation id")
        ));

        var resolver = mock(DatabaseApplicationScopeResolver.class);
        when(resolver.resolveDiscoveryScope("sandbox-a", "crm-service")).thenReturn(new ResolvedDatabaseApplicationScope(
                "sandbox-a",
                "oracle",
                "crm-service",
                List.of("CRM_APP"),
                List.of(),
                List.of("applicationPattern matched crm-service -> CRM_APP"),
                List.of()
        ));
        when(resolver.requiredEnvironment("sandbox-a")).thenReturn(environment("oracle"));

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
                new DbFindColumnsRequest("crm-service", "customer_interaction", null, "correlationId", 50)
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
                        new DbTableRef("crm_app", "customer_interaction"),
                        List.of(
                                new DbFilter("correlation_id", DbOperator.EQ, List.of("corr-123")),
                                new DbFilter("status", DbOperator.STARTS_WITH, List.of("ACT"))
                        )
                )
        );

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryClient).queryForCount(any(), sqlCaptor.capture(), paramsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("SELECT COUNT(*) FROM CRM_APP.CUSTOMER_INTERACTION t WHERE t.CORRELATION_ID = :p0 AND UPPER(t.STATUS) LIKE UPPER(:p1)"));
        assertEquals("corr-123", paramsCaptor.getValue().get("p0"));
        assertEquals("ACT%", paramsCaptor.getValue().get("p1"));
        assertEquals(3L, result.count());
    }

    @Test
    void shouldBuildCountRowsSqlForClobNotEqualFilter() {
        var guard = guardWithColumnTypes(Map.of("RELATED_CUSTOMERS", "CLOB"));
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

        service.countRows(
                scope(),
                new DbCountRowsRequest(
                        new DbTableRef("crm_app", "customer_entity"),
                        List.of(
                                new DbFilter("related_customers", DbOperator.NE, List.of("[]")),
                                new DbFilter("related_customers", DbOperator.IS_NOT_NULL, List.of())
                        )
                )
        );

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryClient).queryForCount(any(), sqlCaptor.capture(), paramsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains(
                "DBMS_LOB.COMPARE(t.RELATED_CUSTOMERS, TO_CLOB(:p0)) <> 0 AND t.RELATED_CUSTOMERS IS NOT NULL"
        ));
        assertEquals("[]", paramsCaptor.getValue().get("p0"));
    }

    @Test
    void shouldUseLengthCheckForClobNotEmptyFilter() {
        var guard = guardWithColumnTypes(Map.of("RELATED_CUSTOMERS", "CLOB"));
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

        service.countRows(
                scope(),
                new DbCountRowsRequest(
                        new DbTableRef("crm_app", "customer_entity"),
                        List.of(new DbFilter("related_customers", DbOperator.NE, List.of("")))
                )
        );

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryClient).queryForCount(any(), sqlCaptor.capture(), paramsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("DBMS_LOB.GETLENGTH(t.RELATED_CUSTOMERS) > 0"));
        assertTrue(paramsCaptor.getValue().isEmpty());
    }

    @Test
    void shouldRejectRangeComparisonOnClobFilter() {
        var service = new DatabaseToolService(
                properties(),
                baseResolver(),
                mock(DatabaseMetadataClient.class),
                mock(DatabaseReadOnlyQueryClient.class),
                guardWithColumnTypes(Map.of("RELATED_CUSTOMERS", "CLOB")),
                new DatabaseResultMasker(),
                new DatabaseResultLimiter(properties())
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> service.countRows(
                scope(),
                new DbCountRowsRequest(
                        new DbTableRef("crm_app", "customer_entity"),
                        List.of(new DbFilter("related_customers", DbOperator.GT, List.of("[]")))
                )
        ));

        assertTrue(exception.getMessage().contains("does not support GT typed filters"));
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
                        new DbTableRef("crm_app", "customer_interaction"),
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
        when(metadata.describeTable("sandbox-a", "CRM_APP", "CUSTOMER_INTERACTION")).thenReturn(new TableDescriptionMetadata(
                "CRM_APP",
                "CUSTOMER_INTERACTION",
                "TABLE",
                "customer interaction",
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
        row.put("CUSTOMER_ID", "CUST-1");
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
                        new DbTableRef("crm_app", "customer_interaction"),
                        "customer_id",
                        new DbTableRef("crm_app", "customer_profile"),
                        "id",
                        List.of(new DbFilter("tenant_id", DbOperator.EQ, List.of("TENANT_A"))),
                        5
                )
        );

        verify(queryClient).queryForCount(any(), org.mockito.ArgumentMatchers.contains("LEFT JOIN CRM_APP.CUSTOMER_PROFILE p"), anyMap());
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
                                new DbTableRef("crm_app", "customer_interaction"),
                                new DbTableRef("common_dict", "customer_status")
                        ),
                        List.of(new DbJoinCondition(
                                new DbColumnRef(new DbTableRef("crm_app", "customer_interaction"), "status_id"),
                                new DbColumnRef(new DbTableRef("common_dict", "customer_status"), "id"),
                                JoinType.INNER
                        )),
                        List.of(new DbQualifiedFilter(
                                new DbColumnRef(new DbTableRef("common_dict", "customer_status"), "status"),
                                DbOperator.EQ,
                                List.of("ACTIVE")
                        ))
                )
        );

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryClient).queryForCount(any(), sqlCaptor.capture(), anyMap());

        assertTrue(sqlCaptor.getValue().contains("FROM CRM_APP.CUSTOMER_INTERACTION t0 INNER JOIN COMMON_DICT.CUSTOMER_STATUS t1 ON t0.STATUS_ID = t1.ID"));
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
        when(resolver.requiredEnvironment("sandbox-a")).thenReturn(environment("oracle"));
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
        when(guard.validateColumnDefinition(any(), any(), anyString())).thenAnswer(invocation -> {
            var column = invocation.getArgument(2, String.class).toUpperCase();
            return new ColumnDefinition(column, "VARCHAR2", 128, null, null, true, null, null);
        });
        when(guard.normalizeIdentifier(anyString())).thenAnswer(invocation -> invocation.getArgument(0, String.class).toUpperCase());
        return guard;
    }

    private DatabaseSqlGuard guardWithColumnTypes(Map<String, String> dataTypes) {
        var guard = baseGuard();
        when(guard.validateColumnDefinition(any(), any(), anyString())).thenAnswer(invocation -> {
            var column = invocation.getArgument(2, String.class).toUpperCase();
            var dataType = dataTypes.getOrDefault(column, "VARCHAR2");
            return new ColumnDefinition(column, dataType, 128, null, null, true, null, null);
        });
        return guard;
    }

    private DbCapabilityScope scope() {
        return new DbCapabilityScope("corr-123", "sandbox-a", "run-1", "analysis-run-1", "tool-call-1", "db_test_tool");
    }
}
