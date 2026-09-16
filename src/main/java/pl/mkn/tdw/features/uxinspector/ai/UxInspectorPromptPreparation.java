package pl.mkn.tdw.features.uxinspector.ai;

import java.util.Map;
import java.util.Set;

public record UxInspectorPromptPreparation(
        String prompt,
        Map<String, String> artifactContents,
        Set<String> initialSourcePaths
) {
    public UxInspectorPromptPreparation {
        artifactContents = artifactContents != null ? Map.copyOf(artifactContents) : Map.of();
        initialSourcePaths = initialSourcePaths != null ? Set.copyOf(initialSourcePaths) : Set.of();
    }
}
