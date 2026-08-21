package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;
import java.util.Objects;

record GitLabFrontendScreenReachabilitySeed(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendSourceRevision sourceRevision,
        GitLabFrontendRouteNode screenNode,
        GitLabFrontendEffectiveRouteChain effectiveRouteChain,
        GitLabFrontendGraphCoverage graphCoverage,
        List<GitLabFrontendSourceFile> sourceFiles,
        List<GitLabFrontendGraphDiagnostic> diagnostics
) {
    GitLabFrontendScreenReachabilitySeed {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision must not be null");
        screenNode = Objects.requireNonNull(screenNode, "screenNode must not be null");
        effectiveRouteChain = Objects.requireNonNull(effectiveRouteChain, "effectiveRouteChain must not be null");
        graphCoverage = Objects.requireNonNull(graphCoverage, "graphCoverage must not be null");
        sourceFiles = sourceFiles != null ? List.copyOf(sourceFiles) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}
