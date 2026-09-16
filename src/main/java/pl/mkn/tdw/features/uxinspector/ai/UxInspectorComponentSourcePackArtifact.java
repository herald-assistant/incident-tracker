package pl.mkn.tdw.features.uxinspector.ai;

import java.util.Set;

public record UxInspectorComponentSourcePackArtifact(
        String markdown,
        int componentCount,
        int fileCount,
        int availableFileCount,
        int unavailableFileCount,
        Set<String> availableSourcePaths
) {
    public UxInspectorComponentSourcePackArtifact {
        markdown = markdown != null ? markdown : "";
        availableSourcePaths = availableSourcePaths != null ? Set.copyOf(availableSourcePaths) : Set.of();
    }
}
