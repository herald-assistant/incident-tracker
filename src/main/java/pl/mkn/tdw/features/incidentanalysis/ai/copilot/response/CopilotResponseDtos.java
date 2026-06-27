package pl.mkn.tdw.features.incidentanalysis.ai.copilot.response;

import java.util.List;

public final class CopilotResponseDtos {

    private CopilotResponseDtos() {
    }

    public record StructuredAnalysisResponse(
            String detectedProblem,
            String affectedProcess,
            String affectedBoundedContext,
            String affectedTeam,
            String functionalAnalysis,
            String technicalAnalysis,
            String confidence,
            List<String> visibilityLimits
    ) {

        public StructuredAnalysisResponse {
            visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        }
    }

    public record ParseResult(
            StructuredAnalysisResponse response,
            boolean structuredResponse,
            boolean fallbackResponseUsed,
            List<String> parsedFields
    ) {

        public ParseResult {
            parsedFields = parsedFields != null ? List.copyOf(parsedFields) : List.of();
        }
    }

}
