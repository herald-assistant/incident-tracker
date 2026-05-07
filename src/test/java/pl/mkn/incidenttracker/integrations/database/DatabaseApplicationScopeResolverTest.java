package pl.mkn.incidenttracker.integrations.database;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseApplicationScopeResolverTest {

    private final DatabaseToolProperties properties = sampleProperties();
    private final DatabaseApplicationScopeResolver resolver = new DatabaseApplicationScopeResolver(properties);

    @Test
    void shouldMatchExactApplicationAlias() {
        var scope = resolver.resolveDiscoveryScope("zt01", "orders-service");

        assertEquals("orders-service", scope.applicationAlias());
        assertEquals(List.of("ORDERS_APP"), scope.resolvedSchemas());
        assertTrue(scope.matchedBecause().stream().anyMatch(reason -> reason.contains("orders-service")));
    }

    @Test
    void shouldMatchByApplicationPatternCaseInsensitive() {
        var scope = resolver.resolveDiscoveryScope("zt01", "Orders API");

        assertEquals("orders-service", scope.applicationAlias());
        assertEquals(List.of("ORDERS_APP"), scope.resolvedSchemas());
    }

    @Test
    void shouldMatchByApplicationPatternsFromDeploymentContainerAndProjectHints() {
        var deploymentScope = resolver.resolveDiscoveryScope("zt01", "orders-worker");
        var containerScope = resolver.resolveDiscoveryScope("zt01", "orders-pod");
        var projectScope = resolver.resolveDiscoveryScope("zt01", "orders-gitlab");

        assertEquals("orders-service", deploymentScope.applicationAlias());
        assertEquals("orders-service", containerScope.applicationAlias());
        assertEquals("orders-service", projectScope.applicationAlias());
    }

    @Test
    void shouldFallbackToAllowedSchemasWhenApplicationPatternIsMissing() {
        var scope = resolver.resolveDiscoveryScope("zt01", null);

        assertEquals(null, scope.applicationAlias());
        assertEquals(List.of("COMMON_DICT", "ORDERS_APP", "ORDERS_REP"), scope.resolvedSchemas());
        assertTrue(scope.warnings().stream().anyMatch(warning -> warning.contains("applicationPattern was not provided")));
    }

    @Test
    void shouldFallbackToAllowedSchemasWhenNoApplicationMatchExists() {
        var scope = resolver.resolveDiscoveryScope("zt01", "missing-app");

        assertEquals(null, scope.applicationAlias());
        assertEquals(List.of("COMMON_DICT", "ORDERS_APP", "ORDERS_REP"), scope.resolvedSchemas());
        assertTrue(scope.warnings().stream().anyMatch(warning -> warning.contains("No configured application mapping matched")));
    }

    @Test
    void shouldWarnAndRestrictWhenMultipleMatchesExist() {
        var scope = resolver.resolveDiscoveryScope("zt01", "orders");

        assertEquals("orders-service, orders-reporting", scope.applicationAlias());
        assertEquals(List.of("ORDERS_APP", "ORDERS_REP"), scope.resolvedSchemas());
        assertTrue(scope.warnings().stream().anyMatch(warning -> warning.contains("matched multiple configured applications")));
    }

    @Test
    void shouldNormalizeSchemasAndRelatedSchemasToUppercase() {
        var scope = resolver.resolveDiscoveryScope("zt01", "orders-reporting");

        assertEquals(List.of("ORDERS_REP"), scope.resolvedSchemas());
        assertEquals(List.of("common_dict"), resolver.requiredEnvironment("zt01")
                .getApplications()
                .get("orders-reporting")
                .getRelatedSchemas());
        assertEquals(List.of("COMMON_DICT"), scope.relatedSchemas());
    }

    @Test
    void shouldResolveCatalogApplicationWithEnvironmentUserSuffixAndSharedConnection() {
        var databaseProperties = new DatabaseToolProperties();
        var connectionDefaults = new DatabaseConnectionProperties();
        connectionDefaults.setDriverClassName("oracle.jdbc.OracleDriver");
        connectionDefaults.setUsername("INCIDENT_TRACKER_RO");
        connectionDefaults.setPassword("secret");
        databaseProperties.setConnectionDefaults(connectionDefaults);

        var sharedDevConnection = new DatabaseConnectionProperties();
        sharedDevConnection.setJdbcUrl("jdbc:oracle:thin:@//dev-host:1521/dev");
        sharedDevConnection.setDescription("Shared dev host");
        databaseProperties.getConnections().put("dev-host", sharedDevConnection);

        var agreementProcess = new DatabaseApplicationProperties();
        agreementProcess.setDatabaseUser("AGREEMENT_PROCESS");
        agreementProcess.setApplicationPatterns(List.of("agreement-process", "agreement-process-api"));
        databaseProperties.getApplications().put("agreement-process", agreementProcess);

        var dev2 = new DatabaseEnvironmentProperties();
        dev2.setConnection("dev-host");
        dev2.setApplicationUserSuffix("_2");
        databaseProperties.getEnvironments().put("dev2", dev2);

        var effectiveEnvironment = databaseProperties.resolveEnvironment("dev2");
        assertEquals("jdbc:oracle:thin:@//dev-host:1521/dev", effectiveEnvironment.getJdbcUrl());
        assertEquals("INCIDENT_TRACKER_RO", effectiveEnvironment.getUsername());
        assertEquals(
                "AGREEMENT_PROCESS_2",
                effectiveEnvironment.getApplications().get("agreement-process").getSchema()
        );

        var scope = new DatabaseApplicationScopeResolver(databaseProperties)
                .resolveDiscoveryScope("dev2", "agreement-process-api");

        assertEquals("agreement-process", scope.applicationAlias());
        assertEquals(List.of("AGREEMENT_PROCESS_2"), scope.resolvedSchemas());
    }

    @Test
    void shouldPreferExactCatalogSchemaOverUserSuffix() {
        var databaseProperties = new DatabaseToolProperties();

        var clauseCheck = new DatabaseApplicationProperties();
        clauseCheck.setDatabaseUser("CLAUSE_CHECK");
        clauseCheck.setSchema("CLAUSE_CHECK_SPECIAL");
        clauseCheck.setApplicationPatterns(List.of("clause-check", "clause-check-dev-special"));
        databaseProperties.getApplications().put("clause-check", clauseCheck);

        var dev1 = new DatabaseEnvironmentProperties();
        dev1.setApplicationUserSuffix("_1");
        databaseProperties.getEnvironments().put("dev1", dev1);

        var scope = new DatabaseApplicationScopeResolver(databaseProperties)
                .resolveDiscoveryScope("dev1", "clause-check-dev-special");

        assertEquals("clause-check", scope.applicationAlias());
        assertEquals(List.of("CLAUSE_CHECK_SPECIAL"), scope.resolvedSchemas());
    }

    @Test
    void shouldBindCurrentConnectionAndApplicationCatalogModel() {
        var environment = new MockEnvironment()
                .withProperty("analysis.database.connection-defaults.username", "INCIDENT_TRACKER_RO")
                .withProperty("analysis.database.connection-defaults.password", "secret")
                .withProperty("analysis.database.connections.dev.jdbc-url", "jdbc:oracle:thin:@//dev-host:1521/dev")
                .withProperty("analysis.database.applications.agreement-process.database-user", "AGREEMENT_PROCESS")
                .withProperty("analysis.database.applications.agreement-process.application-patterns", "agreement-process")
                .withProperty("analysis.database.environments.dev1.connection", "dev")
                .withProperty("analysis.database.environments.dev1.application-user-suffix", "_1");

        var databaseProperties = new DatabaseToolProperties();
        Binder.get(environment).bind("analysis.database", Bindable.ofInstance(databaseProperties));

        var effectiveEnvironment = databaseProperties.resolveEnvironment("dev1");

        assertEquals("jdbc:oracle:thin:@//dev-host:1521/dev", effectiveEnvironment.getJdbcUrl());
        assertEquals(List.of("agreement-process"), effectiveEnvironment.getApplications().keySet().stream().toList());
        assertEquals(
                List.of("AGREEMENT_PROCESS_1"),
                new DatabaseApplicationScopeResolver(databaseProperties)
                        .resolveDiscoveryScope("dev1", "agreement-process")
                        .resolvedSchemas()
        );
    }

    @Test
    void shouldRejectRemovedEnvironmentLevelConnectionAndApplicationMappings() {
        var environment = new MockEnvironment()
                .withProperty("analysis.database.environments.dev1.jdbc-url", "jdbc:oracle:thin:@//legacy-host:1521/legacy")
                .withProperty("analysis.database.environments.dev1.applications.legacy.schema", "LEGACY_SCHEMA");

        var databaseProperties = new DatabaseToolProperties();

        assertThrows(
                BindException.class,
                () -> Binder.get(environment).bind("analysis.database", Bindable.ofInstance(databaseProperties))
        );
    }

    private DatabaseToolProperties sampleProperties() {
        var databaseProperties = new DatabaseToolProperties();
        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setDatabaseAlias("oracle");
        environmentProperties.setAllowedSchemas(List.of("common_dict"));

        var ordersService = new DatabaseApplicationProperties();
        ordersService.setSchema("orders_app");
        ordersService.setApplicationPatterns(List.of(
                "orders-api",
                "orders service",
                "orders-worker",
                "orders-pod",
                "orders-gitlab"
        ));

        var ordersReporting = new DatabaseApplicationProperties();
        ordersReporting.setSchema("orders_rep");
        ordersReporting.setApplicationPatterns(List.of("orders-reporting"));
        ordersReporting.setRelatedSchemas(List.of("common_dict"));

        databaseProperties.getApplications().put("orders-service", ordersService);
        databaseProperties.getApplications().put("orders-reporting", ordersReporting);

        databaseProperties.getEnvironments().put("zt01", environmentProperties);
        return databaseProperties;
    }
}
