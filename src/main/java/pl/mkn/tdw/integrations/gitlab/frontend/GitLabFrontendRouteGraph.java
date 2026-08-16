package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendRouteGraph(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendSourceRevision sourceRevision,
        GitLabFrontendBootstrapRoot bootstrapRoot,
        List<String> rootNodeIds,
        List<GitLabFrontendRouteNode> nodes,
        List<GitLabFrontendRouteGraphEdge> edges,
        List<GitLabFrontendEffectiveRouteChain> effectiveRouteChains,
        List<GitLabFrontendWorkspaceSignal> workspaceSignals,
        GitLabFrontendGraphCoverage coverage,
        List<GitLabFrontendGraphDiagnostic> diagnostics
) {

    public GitLabFrontendRouteGraph {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision must not be null");
        rootNodeIds = rootNodeIds != null ? List.copyOf(rootNodeIds) : List.of();
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        edges = edges != null ? List.copyOf(edges) : List.of();
        effectiveRouteChains = effectiveRouteChains != null ? List.copyOf(effectiveRouteChains) : List.of();
        workspaceSignals = workspaceSignals != null ? List.copyOf(workspaceSignals) : List.of();
        coverage = Objects.requireNonNull(coverage, "coverage must not be null");
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        if (coverage.status() == GitLabFrontendCoverageStatus.READY && bootstrapRoot == null) {
            throw new IllegalArgumentException("ready route graph requires bootstrapRoot");
        }
    }
}
