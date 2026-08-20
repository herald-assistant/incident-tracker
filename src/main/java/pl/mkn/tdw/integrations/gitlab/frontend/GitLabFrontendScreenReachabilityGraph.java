package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendScreenReachabilityGraph(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendSourceRevision sourceRevision,
        String status,
        GitLabFrontendRouteNode screenNode,
        GitLabFrontendEffectiveRouteChain effectiveRouteChain,
        List<GitLabFrontendReachabilityComponentLevel> componentLevels,
        List<GitLabFrontendReachabilityDependency> dependencies,
        List<GitLabFrontendReachabilityEdge> edges,
        List<GitLabFrontendTechnicalSignal> technicalSignals,
        List<GitLabFrontendGraphDiagnostic> diagnostics,
        int sourceFileCount,
        int sourceCharacters,
        int sliceCharacters,
        int outlineCharacters,
        boolean contextLimitReached,
        List<String> limitations,
        String readableOutline
) {
    public GitLabFrontendScreenReachabilityGraph {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision must not be null");
        screenNode = Objects.requireNonNull(screenNode, "screenNode must not be null");
        effectiveRouteChain = Objects.requireNonNull(effectiveRouteChain, "effectiveRouteChain must not be null");
        componentLevels = componentLevels != null ? List.copyOf(componentLevels) : List.of();
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        edges = edges != null ? List.copyOf(edges) : List.of();
        technicalSignals = technicalSignals != null ? List.copyOf(technicalSignals) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        readableOutline = readableOutline != null ? readableOutline : "";
    }
}
