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
        var scope = resolver.resolveDiscoveryScope("sandbox-a", "crm-service");

        assertEquals("crm-service", scope.applicationAlias());
        assertEquals(List.of("CRM_APP"), scope.resolvedSchemas());
        assertTrue(scope.matchedBecause().stream().anyMatch(reason -> reason.contains("crm-service")));
    }

    @Test
    void shouldMatchByApplicationPatternCaseInsensitive() {
        var scope = resolver.resolveDiscoveryScope("sandbox-a", "CRM API");

        assertEquals("crm-service", scope.applicationAlias());
        assertEquals(List.of("CRM_APP"), scope.resolvedSchemas());
    }

    @Test
    void shouldMatchByApplicationPatternsFromDeploymentContainerAndProjectHints() {
        var deploymentScope = resolver.resolveDiscoveryScope("sandbox-a", "crm-worker");
        var containerScope = resolver.resolveDiscoveryScope("sandbox-a", "crm-pod");
        var projectScope = resolver.resolveDiscoveryScope("sandbox-a", "crm-gitlab");

        assertEquals("crm-service", deploymentScope.applicationAlias());
        assertEquals("crm-service", containerScope.applicationAlias());
        assertEquals("crm-service", projectScope.applicationAlias());
    }

    @Test
    void shouldFallbackToAllowedSchemasWhenApplicationPatternIsMissing() {
        var scope = resolver.resolveDiscoveryScope("sandbox-a", null);

        assertEquals(null, scope.applicationAlias());
        assertEquals(List.of("COMMON_DICT", "CRM_APP", "CRM_REPORTING"), scope.resolvedSchemas());
        assertTrue(scope.warnings().stream().anyMatch(warning -> warning.contains("applicationPattern was not provided")));
    }

    @Test
    void shouldFallbackToAllowedSchemasWhenNoApplicationMatchExists() {
        var scope = resolver.resolveDiscoveryScope("sandbox-a", "missing-app");

        assertEquals(null, scope.applicationAlias());
        assertEquals(List.of("COMMON_DICT", "CRM_APP", "CRM_REPORTING"), scope.resolvedSchemas());
        assertTrue(scope.warnings().stream().anyMatch(warning -> warning.contains("No configured application mapping matched")));
    }

    @Test
    void shouldWarnAndRestrictWhenMultipleMatchesExist() {
        var scope = resolver.resolveDiscoveryScope("sandbox-a", "crm");

        assertEquals("crm-service, crm-reporting", scope.applicationAlias());
        assertEquals(List.of("CRM_APP", "CRM_REPORTING"), scope.resolvedSchemas());
        assertTrue(scope.warnings().stream().anyMatch(warning -> warning.contains("matched multiple configured applications")));
    }

    @Test
    void shouldNormalizeSchemasAndRelatedSchemasToUppercase() {
        var scope = resolver.resolveDiscoveryScope("sandbox-a", "crm-reporting");

        assertEquals(List.of("CRM_REPORTING"), scope.resolvedSchemas());
        assertEquals(List.of("common_dict"), resolver.requiredEnvironment("sandbox-a")
                .getApplications()
                .get("crm-reporting")
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
        sharedDevConnection.setJdbcUrl("jdbc:oracle:thin:@//db-dev.example.internal:1521/dev");
        sharedDevConnection.setDescription("Shared dev host");
        databaseProperties.getConnections().put("db-dev.example.internal", sharedDevConnection);

        var crmServiceCatalog = new DatabaseApplicationProperties();
        crmServiceCatalog.setDatabaseUser("CRM_APP");
        crmServiceCatalog.setApplicationPatterns(List.of("crm-service", "crm-service-api"));
        databaseProperties.getApplications().put("crm-service", crmServiceCatalog);

        var sandbox2 = new DatabaseEnvironmentProperties();
        sandbox2.setConnection("db-dev.example.internal");
        sandbox2.setApplicationUserSuffix("_2");
        databaseProperties.getEnvironments().put("sandbox-2", sandbox2);

        var effectiveEnvironment = databaseProperties.resolveEnvironment("sandbox-2");
        assertEquals("jdbc:oracle:thin:@//db-dev.example.internal:1521/dev", effectiveEnvironment.getJdbcUrl());
        assertEquals("INCIDENT_TRACKER_RO", effectiveEnvironment.getUsername());
        assertEquals(
                "CRM_APP_2",
                effectiveEnvironment.getApplications().get("crm-service").getSchema()
        );

        var scope = new DatabaseApplicationScopeResolver(databaseProperties)
                .resolveDiscoveryScope("sandbox-2", "crm-service-api");

        assertEquals("crm-service", scope.applicationAlias());
        assertEquals(List.of("CRM_APP_2"), scope.resolvedSchemas());
    }

    @Test
    void shouldPreferExactCatalogSchemaOverUserSuffix() {
        var databaseProperties = new DatabaseToolProperties();

        var taskCheck = new DatabaseApplicationProperties();
        taskCheck.setDatabaseUser("CRM_TASKS");
        taskCheck.setSchema("CRM_TASKS_SPECIAL");
        taskCheck.setApplicationPatterns(List.of("crm-tasks", "crm-tasks-dev-special"));
        databaseProperties.getApplications().put("crm-tasks", taskCheck);

        var sandbox1 = new DatabaseEnvironmentProperties();
        sandbox1.setApplicationUserSuffix("_1");
        databaseProperties.getEnvironments().put("sandbox-1", sandbox1);

        var scope = new DatabaseApplicationScopeResolver(databaseProperties)
                .resolveDiscoveryScope("sandbox-1", "crm-tasks-dev-special");

        assertEquals("crm-tasks", scope.applicationAlias());
        assertEquals(List.of("CRM_TASKS_SPECIAL"), scope.resolvedSchemas());
    }

    @Test
    void shouldBindCurrentConnectionAndApplicationCatalogModel() {
        var environment = new MockEnvironment()
                .withProperty("analysis.database.connection-defaults.username", "INCIDENT_TRACKER_RO")
                .withProperty("analysis.database.connection-defaults.password", "secret")
                .withProperty("analysis.database.connections.dev.jdbc-url", "jdbc:oracle:thin:@//db-dev.example.internal:1521/dev")
                .withProperty("analysis.database.applications.crm-service.database-user", "CRM_APP")
                .withProperty("analysis.database.applications.crm-service.application-patterns", "crm-service")
                .withProperty("analysis.database.environments.sandbox-1.connection", "dev")
                .withProperty("analysis.database.environments.sandbox-1.application-user-suffix", "_1");

        var databaseProperties = new DatabaseToolProperties();
        Binder.get(environment).bind("analysis.database", Bindable.ofInstance(databaseProperties));

        var effectiveEnvironment = databaseProperties.resolveEnvironment("sandbox-1");

        assertEquals("jdbc:oracle:thin:@//db-dev.example.internal:1521/dev", effectiveEnvironment.getJdbcUrl());
        assertEquals(List.of("crm-service"), effectiveEnvironment.getApplications().keySet().stream().toList());
        assertEquals(
                List.of("CRM_APP_1"),
                new DatabaseApplicationScopeResolver(databaseProperties)
                        .resolveDiscoveryScope("sandbox-1", "crm-service")
                        .resolvedSchemas()
        );
    }

    @Test
    void shouldRejectRemovedEnvironmentLevelConnectionAndApplicationMappings() {
        var environment = new MockEnvironment()
                .withProperty("analysis.database.environments.sandbox-1.jdbc-url", "jdbc:oracle:thin:@//legacy-host:1521/legacy")
                .withProperty("analysis.database.environments.sandbox-1.applications.legacy.schema", "LEGACY_SCHEMA");

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

        var crmService = new DatabaseApplicationProperties();
        crmService.setSchema("crm_app");
        crmService.setApplicationPatterns(List.of(
                "crm-api",
                "crm service",
                "crm-worker",
                "crm-pod",
                "crm-gitlab"
        ));

        var crmReporting = new DatabaseApplicationProperties();
        crmReporting.setSchema("crm_reporting");
        crmReporting.setApplicationPatterns(List.of("crm-reporting"));
        crmReporting.setRelatedSchemas(List.of("common_dict"));

        databaseProperties.getApplications().put("crm-service", crmService);
        databaseProperties.getApplications().put("crm-reporting", crmReporting);

        databaseProperties.getEnvironments().put("sandbox-a", environmentProperties);
        return databaseProperties;
    }
}
