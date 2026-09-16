package pl.mkn.tdw.features.uxinspector.ai;

import java.util.Map;

public record UxInspectorPromptPreparation(String prompt, Map<String, String> artifactContents) {
    public UxInspectorPromptPreparation {
        artifactContents = artifactContents != null ? Map.copyOf(artifactContents) : Map.of();
    }
}
