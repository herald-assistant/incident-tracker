package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

public record RuntimeConfigurationWorkbenchArtifactResponse(
        String previewId,
        String name,
        String mediaType,
        int characterCount,
        boolean truncated,
        String content
) {
}
