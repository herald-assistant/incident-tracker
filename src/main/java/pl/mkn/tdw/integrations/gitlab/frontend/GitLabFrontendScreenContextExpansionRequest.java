package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendScreenContextExpansionRequest(
        GitLabFrontendRepositoryScope scope,
        String expectedRevision,
        String frontierId,
        String ownerPath,
        String symbol,
        List<String> candidatePaths,
        List<String> deliveredSliceIds,
        GitLabFrontendGraphLimits limits
) {
    public GitLabFrontendScreenContextExpansionRequest {
        candidatePaths = candidatePaths != null ? List.copyOf(candidatePaths) : List.of();
        deliveredSliceIds = deliveredSliceIds != null ? List.copyOf(deliveredSliceIds) : List.of();
        limits = limits != null ? limits : GitLabFrontendGraphLimits.defaults();
    }
}
