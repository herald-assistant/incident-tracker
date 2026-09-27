package pl.mkn.tdw.integrations.gitlab.service.frontend;

import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendEffectiveRouteChain;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendGraphCoverage;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendGraphDiagnostic;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendRouteNode;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendSourceFile;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendSourceRevision;
import java.util.List;
import java.util.Objects;

record GitLabFrontendScreenReachabilitySeed(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendSourceRevision sourceRevision,
        GitLabFrontendRouteNode screenNode,
        GitLabFrontendEffectiveRouteChain effectiveRouteChain,
        List<GitLabFrontendRouteNode> routeSubtreeNodes,
        GitLabFrontendGraphCoverage graphCoverage,
        List<GitLabFrontendSourceFile> sourceFiles,
        List<GitLabFrontendGraphDiagnostic> diagnostics
) {
    GitLabFrontendScreenReachabilitySeed {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision must not be null");
        screenNode = Objects.requireNonNull(screenNode, "screenNode must not be null");
        effectiveRouteChain = Objects.requireNonNull(effectiveRouteChain, "effectiveRouteChain must not be null");
        routeSubtreeNodes = routeSubtreeNodes != null ? List.copyOf(routeSubtreeNodes) : List.of();
        graphCoverage = Objects.requireNonNull(graphCoverage, "graphCoverage must not be null");
        sourceFiles = sourceFiles != null ? List.copyOf(sourceFiles) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}
