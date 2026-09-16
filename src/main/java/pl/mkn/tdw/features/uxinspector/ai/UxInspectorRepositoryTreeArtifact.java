package pl.mkn.tdw.features.uxinspector.ai;

import java.util.List;

public record UxInspectorRepositoryTreeArtifact(
        String markdown,
        List<String> filePaths
) {
    public UxInspectorRepositoryTreeArtifact {
        markdown = markdown != null ? markdown : "";
        filePaths = filePaths != null ? List.copyOf(filePaths) : List.of();
    }
}
