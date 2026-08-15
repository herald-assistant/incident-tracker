package pl.mkn.tdw.features.uiexplorer.contract;

import java.util.List;

public record UiExplorerChangePreparationSummary(
        String changeGoal,
        List<String> likelyImpactAreas,
        List<String> decisionsRequired
) {

    public UiExplorerChangePreparationSummary {
        likelyImpactAreas = likelyImpactAreas != null ? List.copyOf(likelyImpactAreas) : List.of();
        decisionsRequired = decisionsRequired != null ? List.copyOf(decisionsRequired) : List.of();
    }
}

