package pl.mkn.tdw.features.uiexplorer.context;

import java.util.List;

public record UiExplorerUnresolvedFrontier(
        String frontierId,
        String ownerPath,
        String symbol,
        String reason,
        List<String> affectedCategories,
        List<String> candidates
) {
    public UiExplorerUnresolvedFrontier {
        affectedCategories = affectedCategories != null ? List.copyOf(affectedCategories) : List.of();
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
    }
}
