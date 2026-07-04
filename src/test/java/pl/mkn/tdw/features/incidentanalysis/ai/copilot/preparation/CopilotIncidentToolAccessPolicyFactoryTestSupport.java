package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import pl.mkn.tdw.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;
import pl.mkn.tdw.integrations.elasticsearch.ElasticConnectionAvailabilityService;
import pl.mkn.tdw.integrations.elasticsearch.ElasticProperties;

final class CopilotIncidentToolAccessPolicyFactoryTestSupport {

    private CopilotIncidentToolAccessPolicyFactoryTestSupport() {
    }

    static CopilotIncidentToolAccessPolicyFactory policyFactoryWithConfiguredElastic() {
        var properties = new ElasticProperties();
        properties.setBaseUrl("https://kibana.example.internal");
        properties.setKibanaSpaceId("default");
        properties.setIndexPattern("logs-*");
        properties.setAuthorizationHeader("ApiKey test");
        return policyFactory(properties);
    }

    static CopilotIncidentToolAccessPolicyFactory policyFactoryWithMissingElasticConfig() {
        return policyFactory(new ElasticProperties());
    }

    private static CopilotIncidentToolAccessPolicyFactory policyFactory(ElasticProperties properties) {
        return new CopilotIncidentToolAccessPolicyFactory(
                new CopilotIncidentEvidenceCoverageEvaluator(),
                new ElasticConnectionAvailabilityService(properties)
        );
    }
}
