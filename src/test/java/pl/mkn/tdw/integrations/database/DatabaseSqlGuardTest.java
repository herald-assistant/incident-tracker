package pl.mkn.tdw.integrations.database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.integrations.database.DatabaseCapabilityDtos.DbTableRef;
import static pl.mkn.tdw.integrations.database.DatabaseCapabilityDtos.DbCapabilityScope;

class DatabaseSqlGuardTest {

    @Test
    void shouldNormalizeValidIdentifiersToUppercase() {
        var guard = guard(true);

        assertEquals("CUSTOMER_ID", guard.normalizeIdentifier("customer_id"));
    }

    @Test
    void shouldRejectInvalidIdentifiers() {
        var guard = guard(true);

        var exception = assertThrows(IllegalArgumentException.class, () -> guard.normalizeIdentifier("customer-id"));

        assertEquals(
                "Oracle identifier 'customer-id' is not allowed. Only simple unquoted identifiers are supported.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSchemaOutsideAllowlist() {
        var guard = guard(true);

        var exception = assertThrows(IllegalArgumentException.class, () ->
                guard.normalizeTableRef(scope(), new DbTableRef("CRM_NOTIFICATIONS_APP", "CUSTOMER_INTERACTION"))
        );

        assertTrue(exception.getMessage().contains("outside the configured allowlist"));
    }

    @Test
    void shouldAcceptReadonlySelectWhenEnabled() {
        var guard = guard(true);

        assertEquals("SELECT * FROM CRM_APP.CUSTOMER_INTERACTION", guard.validateReadonlySql(scope(), "SELECT * FROM CRM_APP.CUSTOMER_INTERACTION"));
        assertEquals("WITH q AS (SELECT 1 FROM dual) SELECT * FROM q", guard.validateReadonlySql(scope(), "WITH q AS (SELECT 1 FROM dual) SELECT * FROM q"));
    }

    @Test
    void shouldRejectMutatingSqlOrSemicolonOrDisabledRawSql() {
        var enabledGuard = guard(true);
        var disabledGuard = guard(false);

        assertThrows(IllegalArgumentException.class, () -> enabledGuard.validateReadonlySql(scope(), "INSERT INTO X VALUES (1)"));
        assertThrows(IllegalArgumentException.class, () -> enabledGuard.validateReadonlySql(scope(), "DELETE FROM X"));
        assertThrows(IllegalArgumentException.class, () -> enabledGuard.validateReadonlySql(scope(), "DROP TABLE X"));
        assertThrows(IllegalArgumentException.class, () -> enabledGuard.validateReadonlySql(scope(), "SELECT * FROM X;"));
        assertThrows(IllegalStateException.class, () -> disabledGuard.validateReadonlySql(scope(), "SELECT * FROM X"));
    }

    private DatabaseSqlGuard guard(boolean rawSqlEnabled) {
        var properties = new DatabaseToolProperties();
        properties.setRawSqlEnabled(rawSqlEnabled);

        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setAllowedSchemas(List.of("CRM_APP"));
        properties.getEnvironments().put("sandbox-a", environmentProperties);

        var resolver = new DatabaseApplicationScopeResolver(properties);
        var metadataClient = mock(DatabaseMetadataClient.class);
        when(metadataClient.tableExists(anyString(), anyString(), anyString())).thenAnswer(invocation ->
                "CRM_APP".equals(invocation.getArgument(1)) && "CUSTOMER_INTERACTION".equals(invocation.getArgument(2))
        );
        when(metadataClient.columnExists(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        return new DatabaseSqlGuard(properties, resolver, metadataClient);
    }

    private DbCapabilityScope scope() {
        return new DbCapabilityScope("corr-123", "sandbox-a", "run-1", "analysis-run-1", "tool-call-1", "db_count_rows");
    }

    private static void assertTrue(boolean value) {
        org.junit.jupiter.api.Assertions.assertTrue(value);
    }
}
