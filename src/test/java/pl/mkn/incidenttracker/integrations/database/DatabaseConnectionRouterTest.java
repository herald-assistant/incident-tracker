package pl.mkn.incidenttracker.integrations.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConnectionRouterTest {

    @Test
    void shouldInferOracleDriverClassFromJdbcUrl() {
        var router = new DatabaseConnectionRouter(new DatabaseToolProperties());
        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setJdbcUrl(" jdbc:oracle:thin:@db-sandbox.example.internal:1551:CRM\u2028");

        assertEquals("oracle.jdbc.OracleDriver", router.resolveDriverClassName(environmentProperties));
    }

    @Test
    void shouldPreferExplicitDriverClassNameWhenConfigured() {
        var router = new DatabaseConnectionRouter(new DatabaseToolProperties());
        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setJdbcUrl("jdbc:oracle:thin:@db-sandbox.example.internal:1551:CRM");
        environmentProperties.setDriverClassName("oracle.jdbc.replay.OracleDataSourceImpl");

        assertEquals(
                "oracle.jdbc.replay.OracleDataSourceImpl",
                router.resolveDriverClassName(environmentProperties)
        );
    }

    @Test
    void shouldWrapDriverInitializationFailureWithHelpfulMessage() {
        var properties = new DatabaseToolProperties();
        var connectionProperties = new DatabaseConnectionProperties();
        connectionProperties.setJdbcUrl("jdbc:example:thin:@db-sandbox.example.internal:1551:CRM");
        connectionProperties.setDriverClassName("missing.Driver");
        connectionProperties.setUsername("CRM");
        connectionProperties.setPassword("secret");
        properties.getConnections().put("sandbox-b-db", connectionProperties);

        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setConnection("sandbox-b-db");
        properties.getEnvironments().put("sandbox-b", environmentProperties);

        var router = new DatabaseConnectionRouter(properties);

        var exception = assertThrows(IllegalStateException.class, () -> router.route("sandbox-b"));

        assertTrue(exception.getMessage().contains("environment 'sandbox-b'"));
        assertTrue(exception.getMessage().contains("missing.Driver"));
        assertTrue(exception.getMessage().contains("runtime classpath"));
        assertTrue(exception.getMessage().contains("Root cause:"));
        assertTrue(exception.getMessage().contains("analysis.database.connections.<connection>.driver-class-name"));
    }

    @Test
    void shouldHintOracleServiceNameUrlForSidStyleThinUrl() {
        var properties = new DatabaseToolProperties();
        var connectionProperties = new DatabaseConnectionProperties();
        connectionProperties.setJdbcUrl("jdbc:oracle:thin:@db-sandbox.example.internal:1551:CRM");
        connectionProperties.setDriverClassName("missing.Driver");
        connectionProperties.setUsername("CRM");
        connectionProperties.setPassword("secret");
        properties.getConnections().put("dev", connectionProperties);

        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setConnection("dev");
        properties.getEnvironments().put("sandbox-1", environmentProperties);

        var router = new DatabaseConnectionRouter(properties);

        var exception = assertThrows(IllegalStateException.class, () -> router.route("sandbox-1"));

        assertTrue(exception.getMessage().contains("The URL uses Oracle SID-style syntax"));
        assertTrue(exception.getMessage().contains("jdbc:oracle:thin:@//db-sandbox.example.internal:1551/CRM"));
        assertTrue(exception.getMessage().contains("Root cause:"));
    }
}
