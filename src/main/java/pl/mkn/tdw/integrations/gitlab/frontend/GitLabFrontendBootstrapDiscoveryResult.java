package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendBootstrapDiscoveryResult(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendCoverageStatus status,
        GitLabFrontendBootstrapRoot root,
        int candidateCount,
        int inspectedSourceCount,
        boolean candidateLimitReached,
        List<GitLabFrontendGraphDiagnostic> diagnostics
) {

    public GitLabFrontendBootstrapDiscoveryResult {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        nonNegative(candidateCount, "candidateCount");
        nonNegative(inspectedSourceCount, "inspectedSourceCount");
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        if (status == GitLabFrontendCoverageStatus.READY && root == null) {
            throw new IllegalArgumentException("ready bootstrap discovery requires root");
        }
        if (status == GitLabFrontendCoverageStatus.BLOCKED && root != null) {
            throw new IllegalArgumentException("blocked bootstrap discovery must not expose root");
        }
    }

    private static void nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
