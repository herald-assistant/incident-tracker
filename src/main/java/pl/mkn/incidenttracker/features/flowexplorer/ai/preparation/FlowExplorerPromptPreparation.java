package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FlowExplorerPromptPreparation(
        String prompt,
        List<CopilotRenderedArtifact> artifacts,
        Map<String, String> artifactContents
) {

    public FlowExplorerPromptPreparation {
        artifacts = artifacts != null ? List.copyOf(artifacts) : List.of();
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
    }
}
