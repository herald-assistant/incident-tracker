package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import pl.mkn.tdw.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;
import pl.mkn.tdw.integrations.database.config.DatabaseToolProperties;
import pl.mkn.tdw.integrations.elasticsearch.service.availability.ElasticConnectionAvailabilityService;
import pl.mkn.tdw.integrations.elasticsearch.config.ElasticProperties;

final class CopilotIncidentToolAccessPolicyFactoryTestSupport {

    private CopilotIncidentToolAccessPolicyFactoryTestSupport() {
    }

    static CopilotIncidentToolAccessPolicyFactory policyFactoryWithConfiguredElastic() {
        return policyFactoryWithConfiguredElastic(false);
    }

    static CopilotIncidentToolAccessPolicyFactory policyFactoryWithConfiguredElastic(boolean rawSqlEnabled) {
        var properties = new ElasticProperties();
        properties.setBaseUrl("https://kibana.example.invalid");
        properties.setKibanaSpaceId("default");
        properties.setIndexPattern("logs-*");
        properties.setAuthorizationHeader("ApiKey test");
        return policyFactory(properties, rawSqlEnabled);
    }

    static CopilotIncidentToolAccessPolicyFactory policyFactoryWithMissingElasticConfig() {
        return policyFactory(new ElasticProperties(), false);
    }

    private static CopilotIncidentToolAccessPolicyFactory policyFactory(
            ElasticProperties properties,
            boolean rawSqlEnabled
    ) {
        var databaseProperties = new DatabaseToolProperties();
        databaseProperties.setRawSqlEnabled(rawSqlEnabled);
        return new CopilotIncidentToolAccessPolicyFactory(
                new CopilotIncidentEvidenceCoverageEvaluator(),
                new ElasticConnectionAvailabilityService(properties),
                databaseProperties
        );
    }
}
