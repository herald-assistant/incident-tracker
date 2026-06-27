package pl.mkn.tdw.features.flowexplorer.ai;

import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;

import java.util.List;
import java.util.Locale;

public record FlowExplorerSectionRefineAiResponse(
        FlowExplorerResultSection section,
        List<String> globalVisibilityLimits,
        List<String> globalOpenQuestions,
        List<String> sourceReferences,
        List<String> followUpPrompts,
        String confidence,
        List<String> changeSummary
) {

    public FlowExplorerSectionRefineAiResponse {
        globalVisibilityLimits = copy(globalVisibilityLimits);
        globalOpenQuestions = copy(globalOpenQuestions);
        sourceReferences = copy(sourceReferences);
        followUpPrompts = copy(followUpPrompts);
        confidence = normalizeConfidence(confidence);
        changeSummary = copy(changeSummary);
    }

    private static List<String> copy(List<String> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    private static String normalizeConfidence(String value) {
        if (value == null || value.isBlank()) {
            return "low";
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "high", "medium", "low" -> normalized;
            default -> "low";
        };
    }
}
