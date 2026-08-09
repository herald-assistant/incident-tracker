package pl.mkn.tdw.features.configdriftviewer.workbench.api;

public record ConfigDriftViewerWorkbenchArtifactResponse(
        String previewId,
        String name,
        String mediaType,
        int characterCount,
        boolean truncated,
        String content
) {
}
