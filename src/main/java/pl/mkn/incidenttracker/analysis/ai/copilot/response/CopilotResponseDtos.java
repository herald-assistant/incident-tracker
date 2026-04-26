package pl.mkn.incidenttracker.analysis.ai.copilot.response;

import java.util.List;

public final class CopilotResponseDtos {

    private CopilotResponseDtos() {
    }

    public record StructuredAnalysisResponse(
            String detectedProblem,
            String summary,
            String recommendedAction,
            String rationale,
            String affectedFunction,
            String affectedProcess,
            String affectedBoundedContext,
            String affectedTeam,
            String confidence,
            List<EvidenceReference> evidenceReferences,
            List<String> visibilityLimits
    ) {

        public StructuredAnalysisResponse {
            evidenceReferences = evidenceReferences != null ? List.copyOf(evidenceReferences) : List.of();
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

    public record EvidenceReference(
            String field,
            String artifactId,
            String itemId,
            String claim
    ) {
    }
}
