package pl.mkn.tdw.features.incidentanalysis.evidence.provider.dynatrace;

import pl.mkn.tdw.features.incidentanalysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.tdw.integrations.dynatrace.DynatraceIncidentPort;

public final class DynatraceEvidenceProviderTestCreator {

    private DynatraceEvidenceProviderTestCreator() {
    }

    public static DynatraceEvidenceProvider create(
            DynatraceIncidentPort dynatraceIncidentPort,
            DeploymentContextResolver deploymentContextResolver
    ) {
        return new DynatraceEvidenceProvider(
                dynatraceIncidentPort,
                deploymentContextResolver
        );
    }
}
