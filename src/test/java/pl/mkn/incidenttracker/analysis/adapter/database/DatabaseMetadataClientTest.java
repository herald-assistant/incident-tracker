package pl.mkn.incidenttracker.analysis.adapter.database;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DatabaseMetadataClientTest {

    private final DatabaseMetadataClient metadataClient = new DatabaseMetadataClient(mock(DatabaseConnectionRouter.class));

    @Test
    void shouldConvertCamelCaseToSnakeUpper() {
        assertEquals("CORRELATION_ID", metadataClient.camelToSnakeUpper("correlationId"));
        assertEquals("ORDER_STATUS_CODE", metadataClient.camelToSnakeUpper("orderStatusCode"));
    }

    @Test
    void shouldInferLikelyKeyColumns() {
        var table = new TableMetadata(
                "ORDERS_APP",
                "ORDER_EVENT",
                "TABLE",
                "Order event",
                4,
                List.of("ID"),
                1,
                0,
                List.of("ID", "ORDER_ID", "CORRELATION_ID", "STATUS")
        );

        assertEquals(List.of("ID", "ORDER_ID", "CORRELATION_ID"), metadataClient.inferLikelyKeyColumns(table));
    }

    @Test
    void shouldInferRelationshipsFromIdColumnsWhenTargetTableExists() {
        var relationships = metadataClient.inferRelationships(
                "ORDERS_APP",
                "ORDER_EVENT",
                List.of(
                        new ColumnDefinition("ID", "NUMBER", null, 19, 0, false, null, null),
                        new ColumnDefinition("ORDER_ID", "NUMBER", null, 19, 0, false, null, null),
                        new ColumnDefinition("CUSTOMER_ID", "NUMBER", null, 19, 0, false, null, null)
                ),
                Set.of("ORDER", "CUSTOMERS")
        );

        assertEquals(2, relationships.size());
        assertTrue(relationships.stream().anyMatch(relationship ->
                "ORDER_ID".equals(relationship.sourceColumn())
                        && "ORDER".equals(relationship.targetTable().tableName())
        ));
        assertTrue(relationships.stream().anyMatch(relationship ->
                "CUSTOMER_ID".equals(relationship.sourceColumn())
                        && "CUSTOMERS".equals(relationship.targetTable().tableName())
        ));
    }
}
