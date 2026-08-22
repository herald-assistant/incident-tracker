package pl.mkn.tdw.features.uiexplorer.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerScreenCatalogInputException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerSourceRefNotFoundException;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnosticSeverity;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendGraphLimits;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteConfigurationKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteGraph;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteGraphDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteNode;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteNodeKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UiExplorerScreenCatalogService {

    private static final int MAX_SYSTEM_ID_LENGTH = 160;
    private static final int MAX_REF_LENGTH = 255;

    private final UiExplorerFrontendCatalogService frontendCatalogService;
    private final GitLabFrontendRouteGraphDiscoveryService routeGraphDiscoveryService;
    private final UiExplorerScreenCatalogCache screenCatalogCache;

    public UiExplorerScreenCatalog loadCatalog(String systemId, String ref) {
        return loadCatalog(systemId, ref, false);
    }

    public UiExplorerScreenCatalog loadCatalog(String systemId, String ref, boolean refreshCache) {
        var normalizedSystemId = required(systemId, "systemId", MAX_SYSTEM_ID_LENGTH);
        var normalizedRef = required(ref, "branch", MAX_REF_LENGTH);
        var frontend = frontendCatalogService.loadCatalog().findFrontend(normalizedSystemId)
                .orElseThrow(() -> new UiExplorerFrontendNotEligibleException(normalizedSystemId));
        var limits = GitLabFrontendGraphLimits.defaults();
        var cacheKey = cacheKey(frontend, normalizedRef, limits);
        if (refreshCache) {
            screenCatalogCache.evict(cacheKey);
        } else {
            var cached = screenCatalogCache.find(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        var scope = new GitLabFrontendRepositoryScope(
                        frontend.gitLabGroup(),
                        frontend.gitLabProjectName(),
                        normalizedRef,
                        frontend.pathPrefixes()
        );

        try {
            var catalog = map(frontend, routeGraphDiscoveryService.discover(scope, limits), limits);
            screenCatalogCache.save(cacheKey, catalog);
            return catalog;
        } catch (GitLabFrontendDiscoveryException exception) {
            if ("FRONTEND_REF_NOT_FOUND".equals(exception.code())) {
                throw new UiExplorerSourceRefNotFoundException(normalizedSystemId, normalizedRef);
            }
            throw exception;
        }
    }

    private UiExplorerScreenCatalogCache.Key cacheKey(
            UiExplorerFrontendRegistration frontend,
            String requestedRef,
            GitLabFrontendGraphLimits limits
    ) {
        return new UiExplorerScreenCatalogCache.Key(
                frontend.systemId(),
                frontend.label(),
                requestedRef,
                frontend.gitLabGroup(),
                frontend.gitLabProjectName(),
                frontend.repositoryId(),
                frontend.projectPath(),
                frontend.searchMode(),
                frontend.pathPrefixes(),
                limits.maxRouteNodes(),
                limits.maxRouteFiles(),
                limits.maxSourceReads(),
                limits.maxAliasResolutions(),
                limits.maxImportDepth()
        );
    }

    private UiExplorerScreenCatalog map(
            UiExplorerFrontendRegistration frontend,
            GitLabFrontendRouteGraph source,
            GitLabFrontendGraphLimits limits
    ) {
        var nodesById = new LinkedHashMap<String, GitLabFrontendRouteNode>();
        source.nodes().forEach(node -> nodesById.put(node.nodeId(), node));
        var screens = source.nodes().stream()
                .filter(node -> node.kind() == GitLabFrontendRouteNodeKind.SCREEN)
                .filter(node -> node.screen() != null)
                .map(node -> new UiExplorerScreenCatalogEntry(
                        node.screen().screenId(),
                        StringUtils.hasText(node.label()) ? node.label() : node.routePattern(),
                        node.routePattern(),
                        parentRoutePattern(node, nodesById),
                        node.status().name(),
                        node.lazyBoundary(),
                        guards(node),
                        node.routeParameters(),
                        node.limitations()
                ))
                .toList();
        var diagnostics = source.diagnostics().stream()
                .map(diagnostic -> new UiExplorerScreenCatalogDiagnostic(
                        diagnostic.severity().name(),
                        diagnostic.code().name(),
                        diagnostic.message(),
                        diagnostic.source() != null ? diagnostic.source().path() : null
                ))
                .toList();
        var limitations = limitations(source, screens);
        var revision = new UiExplorerSourceRevision(
                source.sourceRevision().ref(),
                source.sourceRevision().commitId()
        );
        return new UiExplorerScreenCatalog(
                frontend.systemId(),
                frontend.label(),
                revision,
                status(source, screens),
                screens,
                diagnostics,
                limitations,
                new UiExplorerScreenCatalogBoundary(
                        source.coverage().visitedRouteNodeCount(),
                        source.coverage().visitedRouteFileCount(),
                        source.coverage().sourceReadCount(),
                        source.coverage().aliasResolutionCount(),
                        source.coverage().unresolvedEdgeCount(),
                        source.coverage().limitReached(),
                        limits.maxRouteNodes(),
                        limits.maxRouteFiles(),
                        limits.maxSourceReads(),
                        limits.maxAliasResolutions(),
                        limits.maxImportDepth()
                )
        );
    }

    private UiExplorerScreenCatalogStatus status(
            GitLabFrontendRouteGraph source,
            List<UiExplorerScreenCatalogEntry> screens
    ) {
        if (screens.isEmpty()) {
            return UiExplorerScreenCatalogStatus.BLOCKED;
        }
        var incompleteScreen = source.nodes().stream()
                .filter(node -> node.kind() == GitLabFrontendRouteNodeKind.SCREEN)
                .anyMatch(node -> node.status() != GitLabFrontendDiscoveryStatus.RESOLVED);
        var materialDiagnostic = source.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() != GitLabFrontendDiagnosticSeverity.INFO);
        if (source.coverage().limitReached()
                || !StringUtils.hasText(source.sourceRevision().commitId())
                || incompleteScreen
                || materialDiagnostic) {
            return UiExplorerScreenCatalogStatus.PARTIAL;
        }
        return UiExplorerScreenCatalogStatus.READY;
    }

    private List<String> limitations(
            GitLabFrontendRouteGraph source,
            List<UiExplorerScreenCatalogEntry> screens
    ) {
        var limitations = new ArrayList<String>();
        if (screens.isEmpty()) {
            limitations.add("No selectable screens were resolved from the bounded route catalog.");
        }
        if (source.coverage().limitReached()) {
            limitations.add("Targeted route graph discovery reached a configured traversal limit.");
        }
        if (!StringUtils.hasText(source.sourceRevision().commitId())) {
            limitations.add("The exact GitLab source revision could not be confirmed.");
        }
        limitations.addAll(source.coverage().limitations());
        return List.copyOf(limitations);
    }

    private String parentRoutePattern(
            GitLabFrontendRouteNode node,
            Map<String, GitLabFrontendRouteNode> nodesById
    ) {
        var parent = node.parentNodeId() != null ? nodesById.get(node.parentNodeId()) : null;
        return parent != null ? parent.routePattern() : "/";
    }

    private List<String> guards(GitLabFrontendRouteNode node) {
        return node.configuration().stream()
                .filter(configuration -> switch (configuration.kind()) {
                    case CAN_ACTIVATE, CAN_ACTIVATE_CHILD, CAN_DEACTIVATE, CAN_MATCH, CAN_LOAD -> true;
                    default -> false;
                })
                .flatMap(configuration -> configuration.referencedSymbols().stream())
                .distinct()
                .toList();
    }

    private String required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new UiExplorerScreenCatalogInputException(field + " must not be blank");
        }
        var normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new UiExplorerScreenCatalogInputException(
                    field + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }
}
