package pl.mkn.tdw.features.configdriftviewer.ai.preparation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public record ConfigDriftViewerPromptPreparation(
        String prompt,
        Map<String, String> artifactContents,
        List<String> visibilityLimits
) {

    public ConfigDriftViewerPromptPreparation {
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
