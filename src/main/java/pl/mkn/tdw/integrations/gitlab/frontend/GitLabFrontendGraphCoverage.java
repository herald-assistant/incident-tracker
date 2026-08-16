package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendGraphCoverage(
        GitLabFrontendCoverageStatus status,
        int visitedRouteNodeCount,
        int visitedRouteFileCount,
        int sourceReadCount,
        int aliasResolutionCount,
        int unresolvedEdgeCount,
        boolean limitReached,
        List<String> limitations
) {

    public GitLabFrontendGraphCoverage {
        status = Objects.requireNonNull(status, "status must not be null");
        nonNegative(visitedRouteNodeCount, "visitedRouteNodeCount");
        nonNegative(visitedRouteFileCount, "visitedRouteFileCount");
        nonNegative(sourceReadCount, "sourceReadCount");
        nonNegative(aliasResolutionCount, "aliasResolutionCount");
        nonNegative(unresolvedEdgeCount, "unresolvedEdgeCount");
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    private static void nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
