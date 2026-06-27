package pl.mkn.tdw.features.flowexplorer.ai;

import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public record FlowExplorerAiResponse(
        FlowExplorerAnalysisGoal goal,
        String audience,
        FlowExplorerResultOverview overview,
        List<FlowExplorerResultSection> sections,
        List<String> globalVisibilityLimits,
        List<String> globalOpenQuestions,
        List<String> sourceReferences,
        String confidence,
        List<String> followUpPrompts
) {

    public FlowExplorerAiResponse {
        goal = goal != null ? goal : FlowExplorerAnalysisGoal.DEEP_DISCOVERY;
        audience = audience != null ? audience : "business_or_system_analyst_tester";
        overview = overview != null ? overview : new FlowExplorerResultOverview("", "low", List.of());
        sections = sections != null ? List.copyOf(sections) : List.of();
        globalVisibilityLimits = copy(globalVisibilityLimits);
        globalOpenQuestions = copy(globalOpenQuestions);
        sourceReferences = copy(sourceReferences);
        confidence = normalizeConfidence(confidence);
        followUpPrompts = copy(followUpPrompts);
    }

    public FlowExplorerAiResponse(
            FlowExplorerAnalysisGoal goal,
            String audience,
            FlowExplorerResultOverview overview,
            List<FlowExplorerResultSection> sections,
            List<String> globalVisibilityLimits,
            List<String> globalOpenQuestions,
            List<String> sourceReferences,
            String confidence
    ) {
        this(
                goal,
                audience,
                overview,
                sections,
                globalVisibilityLimits,
                globalOpenQuestions,
                sourceReferences,
                confidence,
                List.of()
        );
    }

    public static FlowExplorerAiResponse parseFallback(String visibilityLimit) {
        return new FlowExplorerAiResponse(
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                "business_or_system_analyst_tester",
                new FlowExplorerResultOverview(
                        "Nie udalo sie sparsowac odpowiedzi AI do kontraktu Flow Explorer.",
                        "low",
                        List.of()
                ),
                List.of(),
                List.of(visibilityLimit),
                List.of("Popros AI o ponowienie odpowiedzi w wymaganym formacie JSON."),
                List.of(),
                "low",
                List.of("Sprobuj ponownie wygenerowac wynik Flow Explorera w wymaganym formacie JSON, bez dodatkowego tekstu poza JSON.")
        );
    }

    public FlowExplorerAiResponse withRequestContext(
            FlowExplorerAnalysisGoal requestedGoal,
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        var assignments = FlowExplorerResultSectionModeResolver.activeOnly(sectionModes);
        var sectionsById = new LinkedHashMap<FlowExplorerResultSectionId, FlowExplorerResultSection>();
        for (var section : sections) {
            if (section != null && section.id() != null) {
                sectionsById.putIfAbsent(section.id(), section);
            }
        }

        var normalizedSections = assignments.stream()
                .map(assignment -> sectionForAssignment(sectionsById.get(assignment.id()), assignment))
                .toList();
        return new FlowExplorerAiResponse(
                requestedGoal != null ? requestedGoal : goal,
                audience,
                overview,
                normalizedSections,
                globalVisibilityLimits,
                globalOpenQuestions,
                sourceReferences,
                confidence,
                followUpPrompts
        );
    }

    private FlowExplorerResultSection sectionForAssignment(
            FlowExplorerResultSection section,
            FlowExplorerResultSectionModeAssignment assignment
    ) {
        if (section == null) {
            return new FlowExplorerResultSection(
                    assignment.id(),
                    assignment.title(),
                    assignment.mode(),
                    "",
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
        return section.withMode(assignment);
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
