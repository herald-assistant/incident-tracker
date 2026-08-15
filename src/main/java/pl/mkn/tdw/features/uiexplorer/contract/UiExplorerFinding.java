package pl.mkn.tdw.features.uiexplorer.contract;

import java.util.List;

public record UiExplorerFinding(
        String title,
        String description,
        UiExplorerClaimConfidence confidence,
        List<String> conditions,
        List<String> impactNotes,
        List<UiExplorerSourceReference> sourceReferences
) {

    public UiExplorerFinding {
        conditions = conditions != null ? List.copyOf(conditions) : List.of();
        impactNotes = impactNotes != null ? List.copyOf(impactNotes) : List.of();
        sourceReferences = sourceReferences != null ? List.copyOf(sourceReferences) : List.of();
    }
}

