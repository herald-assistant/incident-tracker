package pl.mkn.tdw.features.configdriftviewer.deep;

import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeGrounding;

import java.util.List;

record ConfigDriftViewerCodeSearchResult(
        List<ConfigDriftViewerCodeGrounding> groundings,
        int repositoriesSearched,
        int keysSearched,
        int filesInspected,
        List<String> visibilityLimits
) {

    ConfigDriftViewerCodeSearchResult {
        groundings = groundings != null ? List.copyOf(groundings) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
