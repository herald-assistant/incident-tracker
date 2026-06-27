package pl.mkn.tdw.aiplatform.copilot.runtime;

public record CopilotRenderedArtifact(
        String displayName,
        String role,
        String provider,
        String category,
        Integer itemCount,
        String mimeType,
        String content
) {
}
