package pl.mkn.tdw.features.incidentanalysis.evidence.provider.dynatrace;

import pl.mkn.tdw.features.incidentanalysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.tdw.integrations.dynatrace.DynatraceIncidentPort;
import pl.mkn.tdw.integrations.dynatrace.DynatraceProperties;

public final class DynatraceEvidenceProviderTestCreator {

    private DynatraceEvidenceProviderTestCreator() {
    }

    public static DynatraceEvidenceProvider create(
            DynatraceIncidentPort dynatraceIncidentPort,
            DeploymentContextResolver deploymentContextResolver
    ) {
        return new DynatraceEvidenceProvider(
                dynatraceIncidentPort,
                configuredProperties(),
                deploymentContextResolver
        );
    }

    private static DynatraceProperties configuredProperties() {
        var properties = new DynatraceProperties();
        properties.setBaseUrl("https://dynatrace.test");
        properties.setApiToken("test-token");
        return properties;
    }
}
