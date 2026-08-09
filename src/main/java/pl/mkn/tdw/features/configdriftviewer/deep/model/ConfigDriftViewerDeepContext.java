package pl.mkn.tdw.features.configdriftviewer.deep.model;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution;

import java.util.List;

public record ConfigDriftViewerDeepContext(
        ConfigDriftViewerDeepContextStatus status,
        ConfigDriftViewerDeepPreflight preflight,
        ConfigDriftViewerPrimarySystem primarySystem,
        List<ConfigDriftViewerAffectedEntity> affectedSystems,
        List<ConfigDriftViewerAffectedEntity> integrations,
        List<ConfigDriftViewerAffectedEntity> processes,
        List<ConfigDriftViewerAffectedEntity> boundedContexts,
        List<ConfigDriftViewerCodeGrounding> codeGrounding,
        OperationalContextOwnershipResolution ownership,
        ConfigDriftViewerDeepCoverage coverage,
        List<String> visibilityLimits
) {

    public ConfigDriftViewerDeepContext {
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
