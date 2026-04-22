package pl.mkn.incidenttracker.analysis.adapter.database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldMatchByApplicationNamePatternCaseInsensitive() {
        var scope = resolver.resolveDiscoveryScope("zt01", "Orders API");

        assertEquals("orders-service", scope.applicationAlias());
        assertEquals(List.of("ORDERS_APP"), scope.resolvedSchemas());
    }

    @Test
    void shouldMatchByDeploymentContainerAndProjectPatterns() {
        var deploymentScope = resolver.resolveDiscoveryScope("zt01", "orders-worker");
        var containerScope = resolver.resolveDiscoveryScope("zt01", "orders-pod");
        var projectScope = resolver.resolveDiscoveryScope("zt01", "orders-gitlab");

        assertEquals("orders-service", deploymentScope.applicationAlias());
        assertEquals("orders-service", containerScope.applicationAlias());
        assertEquals("orders-service", projectScope.applicationAlias());
    }

    @Test
    void shouldFallbackToAllowedSchemasWhenApplicationNamePatternIsMissing() {
        var scope = resolver.resolveDiscoveryScope("zt01", null);

        assertEquals(null, scope.applicationAlias());
        assertEquals(List.of("COMMON_DICT", "ORDERS_APP", "ORDERS_REP"), scope.resolvedSchemas());
        assertTrue(scope.warnings().stream().anyMatch(warning -> warning.contains("applicationNamePattern was not provided")));
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

    private DatabaseToolProperties sampleProperties() {
        var databaseProperties = new DatabaseToolProperties();
        var environmentProperties = new DatabaseEnvironmentProperties();
        environmentProperties.setDatabaseAlias("oracle");
        environmentProperties.setAllowedSchemas(List.of("common_dict"));

        var ordersService = new DatabaseApplicationProperties();
        ordersService.setSchema("orders_app");
        ordersService.setApplicationNamePatterns(List.of("orders-api", "orders service"));
        ordersService.setDeploymentNamePatterns(List.of("orders-worker"));
        ordersService.setContainerNamePatterns(List.of("orders-pod"));
        ordersService.setProjectNamePatterns(List.of("orders-gitlab"));

        var ordersReporting = new DatabaseApplicationProperties();
        ordersReporting.setSchema("orders_rep");
        ordersReporting.setApplicationNamePatterns(List.of("orders-reporting"));
        ordersReporting.setRelatedSchemas(List.of("common_dict"));

        var applications = new java.util.LinkedHashMap<String, DatabaseApplicationProperties>();
        applications.put("orders-service", ordersService);
        applications.put("orders-reporting", ordersReporting);
        environmentProperties.setApplications(applications);

        databaseProperties.getEnvironments().put("zt01", environmentProperties);
        return databaseProperties;
    }
}
