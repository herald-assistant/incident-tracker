package pl.mkn.tdw.features.uiexplorer.contract;

import java.util.List;

public record UiExplorerFinding(
        String title,
        String description,
        UiExplorerClaimConfidence confidence,
        List<String> conditions,
        List<UiExplorerSourceReference> sourceReferences
) {

    public UiExplorerFinding {
        conditions = conditions != null ? List.copyOf(conditions) : List.of();
        sourceReferences = sourceReferences != null ? List.copyOf(sourceReferences) : List.of();
    }
}
