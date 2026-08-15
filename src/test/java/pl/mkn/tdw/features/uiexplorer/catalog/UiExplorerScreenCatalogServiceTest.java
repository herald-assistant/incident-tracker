package pl.mkn.tdw.features.uiexplorer.catalog;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerScreenCatalogInputException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerSourceRefNotFoundException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnostic;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnosticSeverity;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteCatalog;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteCatalogRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteEntry;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteEntryKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceReference;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendWorkspaceSignal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.crmCatalogWithoutPrimary;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.eligibleCrmCatalog;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.eligibleCrmPathPrefixCatalog;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.port;

class UiExplorerScreenCatalogServiceTest {

    @Test
    void shouldResolveCrmFrontendScopeAndReturnBusinessScreenCatalog() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        when(discovery.discoverCatalog(any())).thenReturn(resolvedCatalog(false));
        var service = service(eligibleCrmCatalog(), discovery);

        var result = service.loadCatalog("crm-agent-portal", "release/2026.08");

        assertThat(result.systemId()).isEqualTo("crm-agent-portal");
        assertThat(result.systemLabel()).isEqualTo("CRM Agent Portal");
        assertThat(result.sourceRevision().branch()).isEqualTo("release/2026.08");
        assertThat(result.sourceRevision().revision()).isEqualTo("crm-ui-revision-20260815");
        assertThat(result.status()).isEqualTo(UiExplorerScreenCatalogStatus.READY);
        assertThat(result.screens()).singleElement().satisfies(screen -> {
            assertThat(screen.screenId()).isEqualTo("crm-customer-profile");
            assertThat(screen.label()).isEqualTo("Customer profile");
            assertThat(screen.routePattern()).isEqualTo("/crm/customers/:customerId");
            assertThat(screen.guards()).containsExactly("CrmAgentGuard");
        });
        assertThat(result.boundary().maxInventoryFiles()).isEqualTo(2_000);
        assertThat(result.boundary().maxRouteFiles()).isEqualTo(80);

        var requestCaptor = ArgumentCaptor.forClass(GitLabFrontendRouteCatalogRequest.class);
        verify(discovery).discoverCatalog(requestCaptor.capture());
        assertThat(requestCaptor.getValue().scope()).satisfies(scope -> {
            assertThat(scope.group()).isEqualTo("crm");
            assertThat(scope.projectName()).isEqualTo("agent-portal");
            assertThat(scope.ref()).isEqualTo("release/2026.08");
            assertThat(scope.pathPrefixes()).isEmpty();
        });
    }

    @Test
    void shouldExposeBoundedAndDynamicDiscoveryAsPartial() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        when(discovery.discoverCatalog(any())).thenReturn(resolvedCatalog(true));

        var result = service(eligibleCrmCatalog(), discovery)
                .loadCatalog("crm-agent-portal", "release/2026.08");

        assertThat(result.status()).isEqualTo(UiExplorerScreenCatalogStatus.PARTIAL);
        assertThat(result.boundary().routeCatalogTruncated()).isTrue();
        assertThat(result.limitations()).contains("Route discovery reached the configured route file or entry limit.");
        assertThat(result.diagnostics())
                .extracting(UiExplorerScreenCatalogDiagnostic::code)
                .contains("CRM_DYNAMIC_ROUTE_PARTIAL");
    }

    @Test
    void shouldPreserveOperationalContextPathPrefixBoundary() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        when(discovery.discoverCatalog(any())).thenReturn(resolvedCatalog(false));

        service(eligibleCrmPathPrefixCatalog(), discovery)
                .loadCatalog("crm-agent-portal", "release/2026.08");

        var requestCaptor = ArgumentCaptor.forClass(GitLabFrontendRouteCatalogRequest.class);
        verify(discovery).discoverCatalog(requestCaptor.capture());
        assertThat(requestCaptor.getValue().scope().pathPrefixes())
                .containsExactly("apps/crm-agent", "libs/crm-ui");
    }

    @Test
    void shouldRejectFrontendWithoutCompleteOperationalContextBeforeCallingGitLab() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);

        assertThatThrownBy(() -> service(crmCatalogWithoutPrimary(), discovery)
                .loadCatalog("crm-agent-portal", "main"))
                .isInstanceOf(UiExplorerFrontendNotEligibleException.class)
                .hasMessageContaining("crm-agent-portal");

        verifyNoInteractions(discovery);
    }

    @Test
    void shouldMapMissingCrmRefToFeatureOwnedNotFoundError() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        when(discovery.discoverCatalog(any())).thenThrow(new GitLabFrontendDiscoveryException(
                "FRONTEND_REF_NOT_FOUND",
                "The requested GitLab branch/ref does not exist"
        ));

        assertThatThrownBy(() -> service(eligibleCrmCatalog(), discovery)
                .loadCatalog("crm-agent-portal", "release/crm-missing"))
                .isInstanceOf(UiExplorerSourceRefNotFoundException.class)
                .hasMessageContaining("release/crm-missing");
    }

    @Test
    void shouldRejectBlankScopeWithoutCallingDependencies() {
        var frontendCatalog = mock(UiExplorerFrontendCatalogService.class);
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        var service = new UiExplorerScreenCatalogService(frontendCatalog, discovery);

        assertThatThrownBy(() -> service.loadCatalog(" ", "main"))
                .isInstanceOf(UiExplorerScreenCatalogInputException.class);
        verifyNoInteractions(frontendCatalog, discovery);
    }

    private UiExplorerScreenCatalogService service(
            pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog catalog,
            GitLabFrontendSourceDiscoveryService discovery
    ) {
        return new UiExplorerScreenCatalogService(
                new UiExplorerFrontendCatalogService(port(catalog)),
                discovery
        );
    }

    private GitLabFrontendRouteCatalog resolvedCatalog(boolean partial) {
        var scope = new GitLabFrontendRepositoryScope(
                "crm", "agent-portal", "release/2026.08", List.of()
        );
        var screen = new GitLabFrontendRouteEntry(
                "crm-customer-profile",
                "Customer profile",
                "/crm/customers/:customerId",
                "/crm/customers",
                GitLabFrontendRouteEntryKind.SCREEN,
                partial ? GitLabFrontendDiscoveryStatus.PARTIAL : GitLabFrontendDiscoveryStatus.RESOLVED,
                true,
                List.of("CrmAgentGuard"),
                List.of("customerId"),
                null,
                "CrmCustomerProfileComponent",
                "apps/crm-agent/src/app/customer/customer-profile.ts",
                new GitLabFrontendSourceReference(
                        "apps/crm-agent/src/app/app.routes.ts", "crmCustomerRoutes", 12, 22
                ),
                partial ? List.of("A synthetic CRM route fragment is dynamic.") : List.of()
        );
        var diagnostics = partial
                ? List.of(new GitLabFrontendDiagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        "CRM_DYNAMIC_ROUTE_PARTIAL",
                        "A synthetic CRM route factory could not be resolved statically.",
                        "apps/crm-agent/src/app/app.routes.ts"
                ))
                : List.<GitLabFrontendDiagnostic>of();
        return new GitLabFrontendRouteCatalog(
                scope,
                new GitLabFrontendSourceRevision("release/2026.08", "crm-ui-revision-20260815"),
                List.of(new GitLabFrontendWorkspaceSignal(
                        "FRAMEWORK", "Angular 20", "apps/crm-agent/project.json"
                )),
                List.of(screen),
                diagnostics,
                42,
                1,
                false,
                partial
        );
    }
}
