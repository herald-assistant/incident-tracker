package pl.mkn.tdw.features.uiexplorer.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerScreenCatalogInputException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerSourceRefNotFoundException;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.frontendcatalog.FrontendViewCatalog;
import pl.mkn.tdw.frontendcatalog.FrontendViewCatalogService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendGraphLimits;

@Service
@RequiredArgsConstructor
public class UiExplorerScreenCatalogService {
    private static final int MAX_SYSTEM_ID_LENGTH = 160;
    private static final int MAX_REF_LENGTH = 255;

    private final UiExplorerFrontendCatalogService frontendCatalogService;
    private final FrontendViewCatalogService frontendViewCatalogService;
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
        var key = new UiExplorerScreenCatalogCache.Key(
                frontend.systemId(), frontend.label(), normalizedRef, frontend.gitLabGroup(),
                frontend.gitLabProjectName(), frontend.repositoryId(), frontend.projectPath(), frontend.searchMode(),
                frontend.pathPrefixes(), limits.maxRouteNodes(), limits.maxRouteFiles(), limits.maxSourceReads(),
                limits.maxAliasResolutions(), limits.maxImportDepth());
        if (refreshCache) {
            screenCatalogCache.evict(key);
        } else {
            var cached = screenCatalogCache.find(key);
            if (cached.isPresent()) return cached.get();
        }
        try {
            var mapped = map(frontendViewCatalogService.loadCatalog(normalizedSystemId, normalizedRef));
            screenCatalogCache.save(key, mapped);
            return mapped;
        } catch (GitLabFrontendDiscoveryException exception) {
            if ("FRONTEND_REF_NOT_FOUND".equals(exception.code())) {
                throw new UiExplorerSourceRefNotFoundException(normalizedSystemId, normalizedRef);
            }
            throw exception;
        }
    }

    private UiExplorerScreenCatalog map(FrontendViewCatalog source) {
        return new UiExplorerScreenCatalog(
                source.systemId(), source.systemLabel(),
                new UiExplorerSourceRevision(source.sourceRevision().branch(), source.sourceRevision().revision()),
                UiExplorerScreenCatalogStatus.valueOf(source.status().name()),
                source.views().stream().map(view -> new UiExplorerScreenCatalogEntry(
                        view.viewId(), view.label(), view.routePattern(), view.parentRoutePattern(), view.status(),
                        view.lazyLoaded(), view.guards(), view.routeParameters(), view.limitations())).toList(),
                source.diagnostics().stream().map(value -> new UiExplorerScreenCatalogDiagnostic(
                        value.severity(), value.code(), value.message(), value.sourcePath())).toList(),
                source.limitations(),
                new UiExplorerScreenCatalogBoundary(
                        source.boundary().visitedRouteNodeCount(), source.boundary().visitedRouteFileCount(),
                        source.boundary().sourceReadCount(), source.boundary().aliasResolutionCount(),
                        source.boundary().unresolvedEdgeCount(), source.boundary().limitReached(),
                        source.boundary().maxRouteNodes(), source.boundary().maxRouteFiles(),
                        source.boundary().maxSourceReads(), source.boundary().maxAliasResolutions(),
                        source.boundary().maxImportDepth())
        );
    }

    private String required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) throw new UiExplorerScreenCatalogInputException(field + " must not be blank");
        var normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new UiExplorerScreenCatalogInputException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
