package pl.mkn.incidenttracker.features.flowexplorer.ai;

import java.util.List;

public record FlowExplorerAiResponse(
        String userIntentSummary,
        String audienceSummary,
        FlowExplorerAiEndpointContract endpointContract,
        List<FlowExplorerAiFlowStep> flowSteps,
        List<String> businessRules,
        List<String> validations,
        List<String> persistence,
        List<String> externalIntegrations,
        List<String> testScenarios,
        List<String> risksAndEdgeCases,
        List<String> openQuestions,
        List<String> visibilityLimits,
        List<String> sourceReferences,
        String confidence
) {

    public FlowExplorerAiResponse {
        flowSteps = flowSteps != null ? List.copyOf(flowSteps) : List.of();
        businessRules = copy(businessRules);
        validations = copy(validations);
        persistence = copy(persistence);
        externalIntegrations = copy(externalIntegrations);
        testScenarios = copy(testScenarios);
        risksAndEdgeCases = copy(risksAndEdgeCases);
        openQuestions = copy(openQuestions);
        visibilityLimits = copy(visibilityLimits);
        sourceReferences = copy(sourceReferences);
        confidence = confidence != null ? confidence : "low";
    }

    public static FlowExplorerAiResponse parseFallback(String visibilityLimit) {
        return new FlowExplorerAiResponse(
                null,
                "Nie udalo sie sparsowac odpowiedzi AI do kontraktu Flow Explorer.",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Popros AI o ponowienie odpowiedzi w wymaganym formacie JSON."),
                List.of(visibilityLimit),
                List.of(),
                "low"
        );
    }

    private static List<String> copy(List<String> values) {
        return values != null ? List.copyOf(values) : List.of();
    }
}
