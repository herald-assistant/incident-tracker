package pl.mkn.tdw.features.uiexplorer.contract;

import java.util.List;

public record UiExplorerResultSection(
        UiExplorerSectionId sectionId,
        UiExplorerSectionMode mode,
        UiExplorerCoverageStatus coverage,
        String summary,
        List<UiExplorerFinding> findings,
        List<String> dependencies,
        List<UiExplorerSourceReference> sourceReferences,
        List<String> visibilityLimits,
        List<String> openQuestions
) {

    public UiExplorerResultSection {
        findings = findings != null ? List.copyOf(findings) : List.of();
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        sourceReferences = sourceReferences != null ? List.copyOf(sourceReferences) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        openQuestions = openQuestions != null ? List.copyOf(openQuestions) : List.of();
    }
}

