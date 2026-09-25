package pl.mkn.tdw.aiplatform.copilot.tools.report;

public record CopilotReportSectionChunk(
        String status,
        String message,
        String sectionId,
        String markdownSha256,
        Integer chunkIndex,
        Integer nextChunkIndex,
        boolean hasMore,
        String markdown
) {
}
