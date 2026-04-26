package pl.mkn.incidenttracker.analysis.ai.copilot.response;

import java.util.List;

public record CopilotResponseParseResult(
        CopilotStructuredAnalysisResponse response,
        boolean structuredResponse,
        boolean fallbackResponseUsed,
        List<String> parsedFields
) {

    public CopilotResponseParseResult {
        parsedFields = parsedFields != null ? List.copyOf(parsedFields) : List.of();
    }
}
