package pl.mkn.incidenttracker.integrations.database;

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
        assertEquals("CUSTOMER_STATUS_CODE", metadataClient.camelToSnakeUpper("customerStatusCode"));
    }

    @Test
    void shouldInferLikelyKeyColumns() {
        var table = new TableMetadata(
                "CRM_APP",
                "CUSTOMER_INTERACTION",
                "TABLE",
                "Customer interaction",
                4,
                List.of("ID"),
                1,
                0,
                List.of("ID", "CUSTOMER_PROFILE_ID", "CORRELATION_ID", "STATUS")
        );

        assertEquals(List.of("ID", "CUSTOMER_PROFILE_ID", "CORRELATION_ID"), metadataClient.inferLikelyKeyColumns(table));
    }

    @Test
    void shouldInferRelationshipsFromIdColumnsWhenTargetTableExists() {
        var relationships = metadataClient.inferRelationships(
                "CRM_APP",
                "CUSTOMER_INTERACTION",
                List.of(
                        new ColumnDefinition("ID", "NUMBER", null, 19, 0, false, null, null),
                        new ColumnDefinition("CUSTOMER_PROFILE_ID", "NUMBER", null, 19, 0, false, null, null),
                        new ColumnDefinition("CUSTOMER_ID", "NUMBER", null, 19, 0, false, null, null)
                ),
                Set.of("CUSTOMER_PROFILE", "CUSTOMERS")
        );

        assertEquals(2, relationships.size());
        assertTrue(relationships.stream().anyMatch(relationship ->
                "CUSTOMER_PROFILE_ID".equals(relationship.sourceColumn())
                        && "CUSTOMER_PROFILE".equals(relationship.targetTable().tableName())
        ));
        assertTrue(relationships.stream().anyMatch(relationship ->
                "CUSTOMER_ID".equals(relationship.sourceColumn())
                        && "CUSTOMERS".equals(relationship.targetTable().tableName())
        ));
    }
}
