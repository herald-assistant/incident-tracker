package pl.mkn.tdw.features.uiexplorer.context;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerFrontendCatalogService;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerSourceRefNotFoundException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerScreenSelectionStaleException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerSourceRevisionChangedException;
import pl.mkn.tdw.features.uiexplorer.contract.*;
import pl.mkn.tdw.integrations.gitlab.frontend.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.*;

class UiExplorerScreenReachabilityContextServiceTest {

    @Test
    void shouldBuildFeatureOwnedCrmReachabilityContextFromSelectedScreenGraph() {
        var discovery = mock(GitLabFrontendScreenReachabilityService.class);
        when(discovery.build(any())).thenReturn(reachabilityGraph(false));
        var service = service(eligibleCrmPathPrefixCatalog(), discovery);

        var result = service.buildContext(
                "crm-agent-portal", "release/2026.08", "screen-crm-contact-preferences",
                "crm-ui-revision-20260815",
                List.of(
                        new UiExplorerSectionModeAssignment(UiExplorerSectionId.OVERVIEW, UiExplorerSectionMode.DEEP),
                        new UiExplorerSectionModeAssignment(UiExplorerSectionId.FORMS_AND_RULES, UiExplorerSectionMode.COMPACT),
                        new UiExplorerSectionModeAssignment(UiExplorerSectionId.DATA_AND_SERVICES, UiExplorerSectionMode.OFF)
                )
        );

        assertThat(result.systemLabel()).isEqualTo("CRM Agent Portal");
        assertThat(result.screen().routePattern()).isEqualTo("/contacts/:contactId/preferences");
        assertThat(result.screen().navigationContext()).isEqualTo("/contacts/:contactId");
        assertThat(result.sourceRevision().revision()).isEqualTo("crm-ui-revision-20260815");
        assertThat(result.status()).isEqualTo(UiExplorerCoverageStatus.READY);
        assertThat(result.guards()).containsExactly("CrmAuthGuard");
        assertThat(result.components()).singleElement().satisfies(component -> {
            assertThat(component.sourcePath()).endsWith("crm-contact-preferences.component.ts");
            assertThat(component.sliceContent()).contains("CrmContactPreferencesComponent");
        });
        assertThat(result.boundary().componentCount()).isEqualTo(1);
        assertThat(result.boundary().sliceCharacters()).isEqualTo(81);

        var request = ArgumentCaptor.forClass(GitLabFrontendScreenSelectionRequest.class);
        verify(discovery).build(request.capture());
        assertThat(request.getValue().screenId()).isEqualTo("screen-crm-contact-preferences");
        assertThat(request.getValue().expectedRevision()).isEqualTo("crm-ui-revision-20260815");
        assertThat(request.getValue().scope().pathPrefixes()).containsExactly("apps/crm-agent", "libs/crm-ui");
    }

    @Test
    void shouldDowngradeCoverageWhenSelectedCrmGraphReachesLimit() {
        var discovery = mock(GitLabFrontendScreenReachabilityService.class);
        when(discovery.build(any())).thenReturn(reachabilityGraph(true));

        var result = service(eligibleCrmPathPrefixCatalog(), discovery).buildContext(
                "crm-agent-portal", "release/2026.08", "screen-crm-contact-preferences",
                "crm-ui-revision-20260815", activeOverview()
        );

        assertThat(result.status()).isEqualTo(UiExplorerCoverageStatus.PARTIAL);
        assertThat(result.boundary().contextLimitReached()).isTrue();
        assertThat(result.visibilityLimits()).isEmpty();
        assertThat(result.researchGaps()).anyMatch(limit -> limit.contains("reached"));
    }

    @Test
    void shouldMapRevisionChangeToFeatureConflict() {
        var discovery = mock(GitLabFrontendScreenReachabilityService.class);
        when(discovery.build(any())).thenThrow(new GitLabFrontendDiscoveryException(
                "FRONTEND_SOURCE_REVISION_CHANGED", "Synthetic CRM revision changed"
        ));
        assertThatThrownBy(() -> service(eligibleCrmPathPrefixCatalog(), discovery).buildContext(
                "crm-agent-portal", "main", "screen-crm-contact-preferences", "crm-old-revision", activeOverview()
        )).isInstanceOf(UiExplorerSourceRevisionChangedException.class);
    }

    @Test
    void shouldMapMissingCrmScreenAndRefToFeatureOwnedErrors() {
        var discovery = mock(GitLabFrontendScreenReachabilityService.class);
        when(discovery.build(any()))
                .thenThrow(new GitLabFrontendDiscoveryException("FRONTEND_SCREEN_NOT_FOUND", "Synthetic CRM screen is stale"))
                .thenThrow(new GitLabFrontendDiscoveryException("FRONTEND_REF_NOT_FOUND", "Synthetic CRM ref is missing"));
        var service = service(eligibleCrmPathPrefixCatalog(), discovery);
        assertThatThrownBy(() -> service.buildContext(
                "crm-agent-portal", "main", "screen-crm-stale", "crm-ui-revision", activeOverview()
        )).isInstanceOf(UiExplorerScreenSelectionStaleException.class);
        assertThatThrownBy(() -> service.buildContext(
                "crm-agent-portal", "release/crm-missing", "screen-crm", "crm-ui-revision", activeOverview()
        )).isInstanceOf(UiExplorerSourceRefNotFoundException.class);
    }

    @Test
    void shouldRejectIncompleteCrmRegistrationBeforeCallingDiscovery() {
        var discovery = mock(GitLabFrontendScreenReachabilityService.class);
        assertThatThrownBy(() -> service(crmCatalogWithoutPrimary(), discovery).buildContext(
                "crm-agent-portal", "main", "screen-crm", "crm-ui-revision", activeOverview()
        )).isInstanceOf(UiExplorerFrontendNotEligibleException.class);
        verifyNoInteractions(discovery);
    }

    private static UiExplorerScreenReachabilityContextService service(
            pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog catalog,
            GitLabFrontendScreenReachabilityService discovery
    ) {
        return new UiExplorerScreenReachabilityContextService(
                new UiExplorerFrontendCatalogService(port(catalog)), discovery
        );
    }

    private static List<UiExplorerSectionModeAssignment> activeOverview() {
        return List.of(new UiExplorerSectionModeAssignment(UiExplorerSectionId.OVERVIEW, UiExplorerSectionMode.DEEP));
    }

    private static GitLabFrontendScreenReachabilityGraph reachabilityGraph(boolean partial) {
        var scope = new GitLabFrontendRepositoryScope(
                "crm", "agent-portal", "release/2026.08", List.of("apps/crm-agent", "libs/crm-ui")
        );
        var routeSource = new GitLabFrontendSourceReference(
                "apps/crm-agent/src/app/app.routes.ts", "crmContactRoutes", 10, 18
        );
        var viewPath = "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts";
        var target = new GitLabFrontendRouteTarget("CrmContactPreferencesComponent", viewPath);
        var screenIdentity = new GitLabFrontendScreenIdentity(
                "screen-crm-contact-preferences", "route-crm-preferences",
                "/contacts/:contactId/preferences", "primary", target
        );
        var guard = new GitLabFrontendRouteConfiguration(
                GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE, "canActivate", List.of("CrmAuthGuard"),
                null, GitLabFrontendDiscoveryStatus.RESOLVED, routeSource, List.of()
        );
        var parentSegment = new GitLabFrontendRouteChainSegment(
                "route-crm-contact", "contacts/:contactId", "/contacts/:contactId", "primary",
                List.of(guard), routeSource
        );
        var screenSegment = new GitLabFrontendRouteChainSegment(
                "route-crm-preferences", "preferences", "/contacts/:contactId/preferences", "primary",
                List.of(), routeSource
        );
        var screenNode = new GitLabFrontendRouteNode(
                "route-crm-preferences", "route-crm-contact", screenIdentity, "Contact preferences", "preferences",
                "/contacts/:contactId/preferences", "primary", GitLabFrontendRouteNodeKind.SCREEN,
                partial ? GitLabFrontendDiscoveryStatus.AMBIGUOUS : GitLabFrontendDiscoveryStatus.RESOLVED,
                true, List.of("contactId"), target, null, null, List.of(), routeSource,
                partial ? List.of("Synthetic CRM view source is ambiguous.") : List.of()
        );
        var diagnostics = partial ? List.of(new GitLabFrontendGraphDiagnostic(
                GitLabFrontendDiagnosticSeverity.WARNING, GitLabFrontendGraphDiagnosticCode.CONTEXT_FILE_LIMIT_REACHED,
                "Synthetic CRM reachability research reached a configured source boundary.",
                screenNode.nodeId(), null, routeSource
        )) : List.<GitLabFrontendGraphDiagnostic>of();
        var component = new GitLabFrontendReachabilityComponent(
                "component-crm-contact-preferences", 0, 0, true, "SELECTED_SCREEN",
                "CrmContactPreferencesComponent", "crm-contact-preferences", viewPath, null,
                partial ? "PARTIAL" : "OK", List.of(), List.of(), List.of(), List.of(), List.of(),
                "export class CrmContactPreferencesComponent { readonly syntheticCrmForm = true; }",
                81, 81, false,
                partial ? List.of("Synthetic CRM reachability research reached a configured source boundary.") : List.of()
        );
        return new GitLabFrontendScreenReachabilityGraph(
                scope, new GitLabFrontendSourceRevision(scope.ref(), "crm-ui-revision-20260815"),
                partial ? "PARTIAL" : "OK", screenNode, new GitLabFrontendEffectiveRouteChain(
                        screenIdentity, List.of(parentSegment, screenSegment), List.of("contactId")
                ),
                List.of(new GitLabFrontendReachabilityComponentLevel(0, List.of(component))),
                List.of(), List.of(), diagnostics,
                1, 81, 81, 32, partial,
                partial ? List.of("Synthetic CRM reachability research reached a configured source boundary.") : List.of(),
                "# Synthetic CRM screen reachability"
        );
    }
}
