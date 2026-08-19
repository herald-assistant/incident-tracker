package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendUnresolvedFrontier(
        String frontierId,
        String ownerPath,
        String symbol,
        String reason,
        List<String> affectedCategories,
        List<String> candidates
) {
    public GitLabFrontendUnresolvedFrontier {
        affectedCategories = affectedCategories != null ? List.copyOf(affectedCategories) : List.of();
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
    }
}
