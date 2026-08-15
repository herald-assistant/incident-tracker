package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record UiExplorerPromptPreparation(
        String prompt,
        List<CopilotRenderedArtifact> artifacts,
        Map<String, String> artifactContents,
        List<String> visibilityLimits
) {

    public UiExplorerPromptPreparation {
        artifacts = artifacts != null ? List.copyOf(artifacts) : List.of();
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
