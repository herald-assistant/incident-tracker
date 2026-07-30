package pl.mkn.tdw.features.runtimeconfigurationverification.deep;

import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeGrounding;

import java.util.List;

record RuntimeConfigurationCodeSearchResult(
        List<RuntimeConfigurationCodeGrounding> groundings,
        int repositoriesSearched,
        int keysSearched,
        int filesInspected,
        List<String> visibilityLimits
) {

    RuntimeConfigurationCodeSearchResult {
        groundings = groundings != null ? List.copyOf(groundings) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
