package pl.mkn.tdw.frontendcatalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.frontend.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FrontendViewCatalogService {
    private final FrontendApplicationCatalogService applicationCatalogService;
    private final GitLabFrontendRouteGraphDiscoveryService routeGraphDiscoveryService;

    public FrontendViewCatalog loadCatalog(String systemId, String ref) {
        var system = required(systemId, "systemId", 160);
        var sourceRef = required(ref, "branch", 255);
        var frontend = applicationCatalogService.loadCatalog().findFrontend(system)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or ineligible frontend system: " + system));
        var limits = GitLabFrontendGraphLimits.defaults();
        var scope = new GitLabFrontendRepositoryScope(frontend.gitLabGroup(), frontend.gitLabProjectName(),
                sourceRef, frontend.pathPrefixes());
        return map(frontend, routeGraphDiscoveryService.discover(scope, limits), limits);
    }

    public FrontendApplicationRegistration requireFrontend(String systemId) {
        var system = required(systemId, "systemId", 160);
        return applicationCatalogService.loadCatalog().findFrontend(system)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or ineligible frontend system: " + system));
    }

    private FrontendViewCatalog map(FrontendApplicationRegistration frontend, GitLabFrontendRouteGraph source,
                                    GitLabFrontendGraphLimits limits) {
        var nodesById = new LinkedHashMap<String, GitLabFrontendRouteNode>();
        source.nodes().forEach(node -> nodesById.put(node.nodeId(), node));
        var views = source.nodes().stream().filter(node -> node.kind() == GitLabFrontendRouteNodeKind.SCREEN)
                .filter(node -> node.screen() != null)
                .map(node -> new FrontendViewCatalog.View(node.screen().screenId(),
                        StringUtils.hasText(node.label()) ? node.label() : node.routePattern(), node.routePattern(),
                        parentRoutePattern(node, nodesById), node.status().name(), node.lazyBoundary(), guards(node),
                        node.routeParameters(), node.limitations())).toList();
        var diagnostics = source.diagnostics().stream().map(value -> new FrontendViewCatalog.Diagnostic(
                value.severity().name(), value.code().name(), value.message(),
                value.source() != null ? value.source().path() : null)).toList();
        var limitations = new ArrayList<String>();
        if (views.isEmpty()) limitations.add("No selectable views were resolved from the bounded route catalog.");
        if (source.coverage().limitReached()) limitations.add("Targeted route graph discovery reached a configured traversal limit.");
        if (!StringUtils.hasText(source.sourceRevision().commitId())) limitations.add("The exact GitLab source revision could not be confirmed.");
        limitations.addAll(source.coverage().limitations());
        var status = status(source, views);
        return new FrontendViewCatalog(frontend.systemId(), frontend.label(),
                new FrontendViewCatalog.SourceRevision(source.sourceRevision().ref(), source.sourceRevision().commitId()),
                status, views, diagnostics, limitations,
                new FrontendViewCatalog.Boundary(source.coverage().visitedRouteNodeCount(),
                        source.coverage().visitedRouteFileCount(), source.coverage().sourceReadCount(),
                        source.coverage().aliasResolutionCount(), source.coverage().unresolvedEdgeCount(),
                        source.coverage().limitReached(), limits.maxRouteNodes(), limits.maxRouteFiles(),
                        limits.maxSourceReads(), limits.maxAliasResolutions(), limits.maxImportDepth()));
    }

    private FrontendViewCatalog.Status status(GitLabFrontendRouteGraph source, List<FrontendViewCatalog.View> views) {
        if (views.isEmpty()) return FrontendViewCatalog.Status.BLOCKED;
        var incomplete = source.nodes().stream().filter(node -> node.kind() == GitLabFrontendRouteNodeKind.SCREEN)
                .anyMatch(node -> node.status() != GitLabFrontendDiscoveryStatus.RESOLVED);
        var diagnostic = source.diagnostics().stream()
                .anyMatch(value -> value.severity() != GitLabFrontendDiagnosticSeverity.INFO);
        return source.coverage().limitReached() || !StringUtils.hasText(source.sourceRevision().commitId())
                || incomplete || diagnostic ? FrontendViewCatalog.Status.PARTIAL : FrontendViewCatalog.Status.READY;
    }

    private String parentRoutePattern(GitLabFrontendRouteNode node, Map<String, GitLabFrontendRouteNode> nodesById) {
        var parent = node.parentNodeId() != null ? nodesById.get(node.parentNodeId()) : null;
        return parent != null ? parent.routePattern() : "/";
    }

    private List<String> guards(GitLabFrontendRouteNode node) {
        return node.configuration().stream().filter(value -> switch (value.kind()) {
            case CAN_ACTIVATE, CAN_ACTIVATE_CHILD, CAN_DEACTIVATE, CAN_MATCH, CAN_LOAD -> true;
            default -> false;
        }).flatMap(value -> value.referencedSymbols().stream()).distinct().toList();
    }

    private String required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(field + " must not be blank");
        var normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        return normalized;
    }
}
