package pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public record RuntimeConfigurationPromptPreparation(
        String prompt,
        Map<String, String> artifactContents,
        List<String> visibilityLimits
) {

    public RuntimeConfigurationPromptPreparation {
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
