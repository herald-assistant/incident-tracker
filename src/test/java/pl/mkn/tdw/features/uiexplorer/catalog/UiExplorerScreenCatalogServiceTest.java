package pl.mkn.tdw.features.uiexplorer.catalog;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerScreenCatalogInputException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerSourceRefNotFoundException;
import pl.mkn.tdw.integrations.gitlab.frontend.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.*;

class UiExplorerScreenCatalogServiceTest {

    @Test
    void shouldResolveCrmFrontendScopeAndReturnGraphFirstBusinessScreenCatalog() {
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        when(discovery.discover(any(), any())).thenReturn(graph(false));
        var service = service(eligibleCrmCatalog(), discovery);

        var result = service.loadCatalog("crm-agent-portal", "release/2026.08");

        assertThat(result.systemId()).isEqualTo("crm-agent-portal");
        assertThat(result.sourceRevision().revision()).isEqualTo("crm-ui-revision-20260815");
        assertThat(result.status()).isEqualTo(UiExplorerScreenCatalogStatus.READY);
        assertThat(result.screens()).singleElement().satisfies(screen -> {
            assertThat(screen.screenId()).startsWith("screen-");
            assertThat(screen.label()).isEqualTo("Customer profile");
            assertThat(screen.routePattern()).isEqualTo("/crm/customers/:customerId");
            assertThat(screen.guards()).containsExactly("CrmAgentGuard");
        });
        assertThat(result.boundary().maxRouteNodes()).isEqualTo(400);
        assertThat(result.boundary().maxRouteFiles()).isEqualTo(80);
        assertThat(result.boundary().sourceReadCount()).isEqualTo(7);

        var scope = ArgumentCaptor.forClass(GitLabFrontendRepositoryScope.class);
        verify(discovery).discover(scope.capture(), any());
        assertThat(scope.getValue()).satisfies(value -> {
            assertThat(value.group()).isEqualTo("crm");
            assertThat(value.projectName()).isEqualTo("agent-portal");
            assertThat(value.ref()).isEqualTo("release/2026.08");
            assertThat(value.pathPrefixes()).isEmpty();
        });
    }

    @Test
    void shouldExposeLimitedGraphAsPartialWithoutRepositoryInventorySemantics() {
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        when(discovery.discover(any(), any())).thenReturn(graph(true));

        var result = service(eligibleCrmCatalog(), discovery)
                .loadCatalog("crm-agent-portal", "release/2026.08");

        assertThat(result.status()).isEqualTo(UiExplorerScreenCatalogStatus.PARTIAL);
        assertThat(result.boundary().limitReached()).isTrue();
        assertThat(result.limitations()).contains("Targeted route graph discovery reached a configured traversal limit.");
        assertThat(result.diagnostics()).extracting(UiExplorerScreenCatalogDiagnostic::code)
                .contains("ROUTE_NODE_LIMIT_REACHED");
    }

    @Test
    void shouldPreserveOperationalContextPathPrefixBoundary() {
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        when(discovery.discover(any(), any())).thenReturn(graph(false));

        service(eligibleCrmPathPrefixCatalog(), discovery)
                .loadCatalog("crm-agent-portal", "release/2026.08");

        var scope = ArgumentCaptor.forClass(GitLabFrontendRepositoryScope.class);
        verify(discovery).discover(scope.capture(), any());
        assertThat(scope.getValue().pathPrefixes()).containsExactly("apps/crm-agent", "libs/crm-ui");
    }

    @Test
    void shouldRejectFrontendWithoutCompleteOperationalContextBeforeCallingGitLab() {
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        assertThatThrownBy(() -> service(crmCatalogWithoutPrimary(), discovery)
                .loadCatalog("crm-agent-portal", "main"))
                .isInstanceOf(UiExplorerFrontendNotEligibleException.class);
        verifyNoInteractions(discovery);
    }

    @Test
    void shouldMapMissingCrmRefToFeatureOwnedNotFoundError() {
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        when(discovery.discover(any(), any())).thenThrow(new GitLabFrontendDiscoveryException(
                "FRONTEND_REF_NOT_FOUND", "Synthetic CRM ref is missing"
        ));
        assertThatThrownBy(() -> service(eligibleCrmCatalog(), discovery)
                .loadCatalog("crm-agent-portal", "release/crm-missing"))
                .isInstanceOf(UiExplorerSourceRefNotFoundException.class);
    }

    @Test
    void shouldRejectBlankScopeWithoutCallingDependencies() {
        var frontendCatalog = mock(UiExplorerFrontendCatalogService.class);
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        var service = new UiExplorerScreenCatalogService(
                frontendCatalog,
                discovery,
                UiExplorerScreenCatalogCache.disabled()
        );
        assertThatThrownBy(() -> service.loadCatalog(" ", "main"))
                .isInstanceOf(UiExplorerScreenCatalogInputException.class);
        verifyNoInteractions(frontendCatalog, discovery);
    }

    @Test
    void shouldReuseCatalogForTheSameCrmScopeAndRefreshOnlyTheMatchingEntry() {
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        when(discovery.discover(any(), any())).thenReturn(graph(false));
        var cache = new InMemoryScreenCatalogCache();
        var service = service(eligibleCrmCatalog(), discovery, cache);

        var first = service.loadCatalog("crm-agent-portal", "release/2026.08");
        var cached = service.loadCatalog("crm-agent-portal", "release/2026.08");
        service.loadCatalog("crm-agent-portal", "crm-review");
        service.loadCatalog("crm-agent-portal", "release/2026.08", true);

        assertThat(cached).isEqualTo(first);
        verify(discovery, times(3)).discover(any(), any());
        assertThat(cache.evictions).isEqualTo(1);
        assertThat(cache.entries).hasSize(2);
    }

    @Test
    void shouldNotCacheFailedCrmDiscovery() {
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        when(discovery.discover(any(), any()))
                .thenThrow(new GitLabFrontendDiscoveryException(
                        "FRONTEND_DISCOVERY_FAILED",
                        "Synthetic CRM route discovery failed"
                ))
                .thenReturn(graph(false));
        var cache = new InMemoryScreenCatalogCache();
        var service = service(eligibleCrmCatalog(), discovery, cache);

        assertThatThrownBy(() -> service.loadCatalog("crm-agent-portal", "crm-review"))
                .isInstanceOf(GitLabFrontendDiscoveryException.class);
        assertThat(cache.entries).isEmpty();

        assertThat(service.loadCatalog("crm-agent-portal", "crm-review").screens()).hasSize(1);
        verify(discovery, times(2)).discover(any(), any());
    }

    @Test
    void shouldNotReuseCatalogAcrossDifferentCrmRepositoryScopes() {
        var discovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        when(discovery.discover(any(), any())).thenReturn(graph(false));
        var cache = new InMemoryScreenCatalogCache();

        service(eligibleCrmCatalog(), discovery, cache)
                .loadCatalog("crm-agent-portal", "release/2026.08");
        service(eligibleCrmPathPrefixCatalog(), discovery, cache)
                .loadCatalog("crm-agent-portal", "release/2026.08");

        verify(discovery, times(2)).discover(any(), any());
        assertThat(cache.entries).hasSize(2);
    }

    private UiExplorerScreenCatalogService service(
            pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog catalog,
            GitLabFrontendRouteGraphDiscoveryService discovery
    ) {
        return service(catalog, discovery, UiExplorerScreenCatalogCache.disabled());
    }

    private UiExplorerScreenCatalogService service(
            pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog catalog,
            GitLabFrontendRouteGraphDiscoveryService discovery,
            UiExplorerScreenCatalogCache cache
    ) {
        return new UiExplorerScreenCatalogService(
                new UiExplorerFrontendCatalogService(port(catalog)),
                discovery,
                cache
        );
    }

    private GitLabFrontendRouteGraph graph(boolean partial) {
        var scope = new GitLabFrontendRepositoryScope("crm", "agent-portal", "release/2026.08", List.of());
        var routeSource = new GitLabFrontendSourceReference(
                "apps/crm-agent/src/app/app.routes.ts", "crmCustomerRoutes", 12, 22
        );
        var target = new GitLabFrontendRouteTarget(
                "CrmCustomerProfileComponent", "apps/crm-agent/src/app/customer/customer-profile.component.ts"
        );
        var screenIdentity = new GitLabFrontendScreenIdentity(
                "screen-crm-customer-profile", "route-crm-customer-profile",
                "/crm/customers/:customerId", "primary", target
        );
        var node = new GitLabFrontendRouteNode(
                "route-crm-customer-profile", null, screenIdentity, "Customer profile", "crm/customers/:customerId",
                "/crm/customers/:customerId", "primary", GitLabFrontendRouteNodeKind.SCREEN,
                partial ? GitLabFrontendDiscoveryStatus.PARTIAL : GitLabFrontendDiscoveryStatus.RESOLVED,
                true, List.of("customerId"), target, null, null,
                List.of(new GitLabFrontendRouteConfiguration(
                        GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE, "canActivate", List.of("CrmAgentGuard"),
                        null, GitLabFrontendDiscoveryStatus.RESOLVED, routeSource, List.of()
                )), routeSource, partial ? List.of("Synthetic CRM route graph is bounded.") : List.of()
        );
        var coverage = new GitLabFrontendGraphCoverage(
                partial ? GitLabFrontendCoverageStatus.PARTIAL : GitLabFrontendCoverageStatus.READY,
                1, 1, 7, 2, partial ? 1 : 0, partial, partial ? List.of("Synthetic CRM graph limit.") : List.of()
        );
        var diagnostics = partial ? List.of(new GitLabFrontendGraphDiagnostic(
                GitLabFrontendDiagnosticSeverity.WARNING, GitLabFrontendGraphDiagnosticCode.ROUTE_NODE_LIMIT_REACHED,
                "Synthetic CRM graph reached maxRouteNodes.", node.nodeId(), null, routeSource
        )) : List.<GitLabFrontendGraphDiagnostic>of();
        return new GitLabFrontendRouteGraph(
                scope, new GitLabFrontendSourceRevision(scope.ref(), "crm-ui-revision-20260815"),
                mock(GitLabFrontendBootstrapRoot.class), List.of(node.nodeId()), List.of(node), List.of(),
                List.of(new GitLabFrontendEffectiveRouteChain(
                        screenIdentity,
                        List.of(new GitLabFrontendRouteChainSegment(
                                node.nodeId(), node.pathSegment(), node.routePattern(), node.outlet(), node.configuration(), routeSource
                        )), List.of("customerId")
                )), List.of(), coverage, diagnostics
        );
    }

    private static final class InMemoryScreenCatalogCache implements UiExplorerScreenCatalogCache {

        private final Map<Key, UiExplorerScreenCatalog> entries = new LinkedHashMap<>();
        private int evictions;

        @Override
        public Optional<UiExplorerScreenCatalog> find(Key key) {
            return Optional.ofNullable(entries.get(key));
        }

        @Override
        public void save(Key key, UiExplorerScreenCatalog catalog) {
            entries.put(key, catalog);
        }

        @Override
        public void evict(Key key) {
            evictions++;
            entries.remove(key);
        }
    }
}
