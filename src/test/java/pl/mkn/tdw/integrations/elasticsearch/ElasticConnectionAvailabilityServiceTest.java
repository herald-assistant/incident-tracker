package pl.mkn.tdw.integrations.elasticsearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticConnectionAvailabilityServiceTest {

    @Test
    void shouldReportConfiguredWhenRequiredPropertiesArePresent() {
        var properties = configuredProperties();

        var availability = new ElasticConnectionAvailabilityService(properties).currentAvailability();

        assertTrue(availability.configured());
        assertTrue(availability.missingPropertyKeys().isEmpty());
        assertNull(availability.disabledReason());
    }

    @Test
    void shouldReportMissingPropertiesWhenRequiredValuesAreAbsent() {
        var properties = new ElasticProperties();
        properties.setKibanaSpaceId("default");
        properties.setIndexPattern("logs-*");

        var availability = new ElasticConnectionAvailabilityService(properties).currentAvailability();

        assertFalse(availability.configured());
        assertTrue(availability.missingPropertyKeys().contains("analysis.elasticsearch.base-url"));
        assertTrue(availability.missingPropertyKeys().contains("analysis.elasticsearch.authorization-header"));
        assertTrue(availability.disabledReason().contains("analysis.elasticsearch.base-url"));
        assertTrue(availability.disabledReason().contains("analysis.elasticsearch.authorization-header"));
    }

    @Test
    void shouldTreatBlankValuesAsMissing() {
        var properties = configuredProperties();
        properties.setBaseUrl(" ");
        properties.setKibanaSpaceId("\t");
        properties.setIndexPattern("");
        properties.setAuthorizationHeader("\n");

        var availability = new ElasticConnectionAvailabilityService(properties).currentAvailability();

        assertFalse(availability.configured());
        assertTrue(availability.missingPropertyKeys().contains("analysis.elasticsearch.base-url"));
        assertTrue(availability.missingPropertyKeys().contains("analysis.elasticsearch.kibana-space-id"));
        assertTrue(availability.missingPropertyKeys().contains("analysis.elasticsearch.index-pattern"));
        assertTrue(availability.missingPropertyKeys().contains("analysis.elasticsearch.authorization-header"));
    }

    private ElasticProperties configuredProperties() {
        var properties = new ElasticProperties();
        properties.setBaseUrl("https://kibana.example.internal");
        properties.setKibanaSpaceId("default");
        properties.setIndexPattern("logs-*");
        properties.setAuthorizationHeader("ApiKey test");
        return properties;
    }
}
