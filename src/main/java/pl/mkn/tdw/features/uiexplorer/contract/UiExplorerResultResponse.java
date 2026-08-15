package pl.mkn.tdw.features.uiexplorer.contract;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.List;

public record UiExplorerResultResponse(
        UiExplorerScreenIdentity screen,
        String scenarioDescription,
        UiExplorerProfile profile,
        UiExplorerSourceRevision sourceRevision,
        String functionalOverview,
        List<UiExplorerResultSection> sections,
        List<UiExplorerCrossSectionDependency> crossSectionDependencies,
        UiExplorerChangePreparationSummary changePreparationSummary,
        UiExplorerClaimConfidence overallConfidence,
        List<String> visibilityLimits,
        List<String> unresolvedQuestions,
        AnalysisAiUsage usage
) {

    public UiExplorerResultResponse {
        sections = sections != null ? List.copyOf(sections) : List.of();
        crossSectionDependencies = crossSectionDependencies != null
                ? List.copyOf(crossSectionDependencies)
                : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        unresolvedQuestions = unresolvedQuestions != null ? List.copyOf(unresolvedQuestions) : List.of();
    }
}
