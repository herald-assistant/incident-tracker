package pl.mkn.incidenttracker.analysis.adapter.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConnectionRouterTest {

    @Test
    void shouldInferOracleDriverClassFromJdbcUrl() {
        var router = new DatabaseConnectionRouter(new DatabaseToolProperties());
        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setJdbcUrl(" jdbc:oracle:thin:@10.151.102.109:1551:CLP\u2028");

        assertEquals("oracle.jdbc.OracleDriver", router.resolveDriverClassName(environmentProperties));
    }

    @Test
    void shouldPreferExplicitDriverClassNameWhenConfigured() {
        var router = new DatabaseConnectionRouter(new DatabaseToolProperties());
        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setJdbcUrl("jdbc:oracle:thin:@10.151.102.109:1551:CLP");
        environmentProperties.setDriverClassName("oracle.jdbc.replay.OracleDataSourceImpl");

        assertEquals(
                "oracle.jdbc.replay.OracleDataSourceImpl",
                router.resolveDriverClassName(environmentProperties)
        );
    }

    @Test
    void shouldWrapDriverInitializationFailureWithHelpfulMessage() {
        var properties = new DatabaseToolProperties();
        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setJdbcUrl("jdbc:example:thin:@10.151.102.109:1551:CLP");
        environmentProperties.setDriverClassName("missing.Driver");
        environmentProperties.setUsername("CLP");
        environmentProperties.setPassword("secret");
        properties.getEnvironments().put("zt002", environmentProperties);

        var router = new DatabaseConnectionRouter(properties);

        var exception = assertThrows(IllegalStateException.class, () -> router.route("zt002"));

        assertTrue(exception.getMessage().contains("environment 'zt002'"));
        assertTrue(exception.getMessage().contains("missing.Driver"));
        assertTrue(exception.getMessage().contains("runtime classpath"));
        assertTrue(exception.getMessage().contains("analysis.database.environments.zt002.driver-class-name"));
    }
}
