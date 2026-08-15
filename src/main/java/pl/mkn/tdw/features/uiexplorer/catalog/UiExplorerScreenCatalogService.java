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
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryLimits;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteCatalog;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteCatalogRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteEntryKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceDiscoveryService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UiExplorerScreenCatalogService {

    private static final int MAX_SYSTEM_ID_LENGTH = 160;
    private static final int MAX_REF_LENGTH = 255;

    private final UiExplorerFrontendCatalogService frontendCatalogService;
    private final GitLabFrontendSourceDiscoveryService sourceDiscoveryService;

    public UiExplorerScreenCatalog loadCatalog(String systemId, String ref) {
        var normalizedSystemId = required(systemId, "systemId", MAX_SYSTEM_ID_LENGTH);
        var normalizedRef = required(ref, "branch", MAX_REF_LENGTH);
        var frontend = frontendCatalogService.loadCatalog().findFrontend(normalizedSystemId)
                .orElseThrow(() -> new UiExplorerFrontendNotEligibleException(normalizedSystemId));
        var limits = GitLabFrontendDiscoveryLimits.defaults();
        var request = new GitLabFrontendRouteCatalogRequest(
                new GitLabFrontendRepositoryScope(
                        frontend.gitLabGroup(),
                        frontend.gitLabProjectName(),
                        normalizedRef,
                        frontend.pathPrefixes()
                ),
                limits
        );

        try {
            return map(frontend, sourceDiscoveryService.discoverCatalog(request), limits);
        } catch (GitLabFrontendDiscoveryException exception) {
            if ("FRONTEND_REF_NOT_FOUND".equals(exception.code())) {
                throw new UiExplorerSourceRefNotFoundException(normalizedSystemId, normalizedRef);
            }
            throw exception;
        }
    }

    private UiExplorerScreenCatalog map(
            UiExplorerFrontendRegistration frontend,
            GitLabFrontendRouteCatalog source,
            GitLabFrontendDiscoveryLimits limits
    ) {
        var screens = source.entries().stream()
                .filter(entry -> entry.kind() == GitLabFrontendRouteEntryKind.SCREEN)
                .filter(entry -> StringUtils.hasText(entry.screenId()))
                .map(entry -> new UiExplorerScreenCatalogEntry(
                        entry.screenId(),
                        entry.label(),
                        entry.routePattern(),
                        entry.parentRoutePattern(),
                        entry.status().name(),
                        entry.lazyLoaded(),
                        entry.guards(),
                        entry.routeParameters(),
                        entry.limitations()
                ))
                .toList();
        var diagnostics = source.diagnostics().stream()
                .map(diagnostic -> new UiExplorerScreenCatalogDiagnostic(
                        diagnostic.severity().name(),
                        diagnostic.code(),
                        diagnostic.message(),
                        diagnostic.sourcePath()
                ))
                .toList();
        var limitations = limitations(source, screens);
        var revision = source.sourceRevision() != null
                ? new UiExplorerSourceRevision(source.sourceRevision().ref(), source.sourceRevision().commitId())
                : new UiExplorerSourceRevision(source.scope().ref(), null);
        return new UiExplorerScreenCatalog(
                frontend.systemId(),
                frontend.label(),
                revision,
                status(source, screens),
                screens,
                diagnostics,
                limitations,
                new UiExplorerScreenCatalogBoundary(
                        source.repositoryFileCount(),
                        source.scannedRouteFileCount(),
                        source.inventoryTruncated(),
                        source.routeCatalogTruncated(),
                        limits.maxInventoryFiles(),
                        limits.maxRouteFiles(),
                        limits.maxRouteEntries()
                )
        );
    }

    private UiExplorerScreenCatalogStatus status(
            GitLabFrontendRouteCatalog source,
            List<UiExplorerScreenCatalogEntry> screens
    ) {
        if (screens.isEmpty()) {
            return UiExplorerScreenCatalogStatus.BLOCKED;
        }
        var incompleteScreen = source.entries().stream()
                .filter(entry -> entry.kind() == GitLabFrontendRouteEntryKind.SCREEN)
                .anyMatch(entry -> entry.status() != GitLabFrontendDiscoveryStatus.RESOLVED);
        var materialDiagnostic = source.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() != GitLabFrontendDiagnosticSeverity.INFO);
        if (source.inventoryTruncated()
                || source.routeCatalogTruncated()
                || source.sourceRevision() == null
                || !StringUtils.hasText(source.sourceRevision().commitId())
                || incompleteScreen
                || materialDiagnostic) {
            return UiExplorerScreenCatalogStatus.PARTIAL;
        }
        return UiExplorerScreenCatalogStatus.READY;
    }

    private List<String> limitations(
            GitLabFrontendRouteCatalog source,
            List<UiExplorerScreenCatalogEntry> screens
    ) {
        var limitations = new ArrayList<String>();
        if (screens.isEmpty()) {
            limitations.add("No selectable screens were resolved from the bounded route catalog.");
        }
        if (source.inventoryTruncated()) {
            limitations.add("Repository inventory reached the configured file limit.");
        }
        if (source.routeCatalogTruncated()) {
            limitations.add("Route discovery reached the configured route file or entry limit.");
        }
        if (source.sourceRevision() == null || !StringUtils.hasText(source.sourceRevision().commitId())) {
            limitations.add("The exact GitLab source revision could not be confirmed.");
        }
        return List.copyOf(limitations);
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
