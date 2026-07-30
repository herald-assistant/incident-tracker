package pl.mkn.tdw.features.runtimeconfigurationverification.deep.model;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution;

import java.util.List;

public record RuntimeConfigurationDeepContext(
        RuntimeConfigurationDeepContextStatus status,
        RuntimeConfigurationDeepPreflight preflight,
        RuntimeConfigurationPrimarySystem primarySystem,
        List<RuntimeConfigurationAffectedEntity> affectedSystems,
        List<RuntimeConfigurationAffectedEntity> integrations,
        List<RuntimeConfigurationAffectedEntity> processes,
        List<RuntimeConfigurationAffectedEntity> boundedContexts,
        List<RuntimeConfigurationCodeGrounding> codeGrounding,
        OperationalContextOwnershipResolution ownership,
        RuntimeConfigurationDeepCoverage coverage,
        List<String> visibilityLimits
) {

    public RuntimeConfigurationDeepContext {
        affectedSystems = copy(affectedSystems);
        integrations = copy(integrations);
        processes = copy(processes);
        boundedContexts = copy(boundedContexts);
        codeGrounding = copy(codeGrounding);
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    private static <T> List<T> copy(List<T> values) {
        return values != null ? List.copyOf(values) : List.of();
    }
}
