package pl.mkn.tdw.api.gitlab.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.integrations.gitlab.frontend.*;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitLabFrontendDiscoveryController.class)
class GitLabFrontendDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitLabFrontendRouteGraphDiscoveryService routeGraphDiscoveryService;

    @MockitoBean
    private GitLabFrontendScreenGraphContextService screenGraphContextService;

    @Test
    void shouldExposeTargetedRouteGraphForSyntheticCrmRepository() throws Exception {
        when(routeGraphDiscoveryService.discover(any(), any())).thenReturn(graph());

        mockMvc.perform(post("/api/gitlab/frontend/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "synthetic-crm",
                                  "projectName": "crm-agent-portal",
                                  "ref": "release/2026.08",
                                  "pathPrefixes": ["apps/crm-agent"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRevision.commitId").value("crm-ui-revision-20260815"))
                .andExpect(jsonPath("$.nodes[0].screen.screenId").value("screen-crm-customer-profile"))
                .andExpect(jsonPath("$.nodes[0].routePattern").value("/crm/customers/:customerId"))
                .andExpect(jsonPath("$.coverage.sourceReadCount").value(7))
                .andExpect(jsonPath("$.repositoryFileCount").doesNotExist())
                .andExpect(jsonPath("$.inventoryTruncated").doesNotExist());

        verify(routeGraphDiscoveryService).discover(argThat(scope ->
                scope.group().equals("synthetic-crm")
                        && scope.projectName().equals("crm-agent-portal")
                        && scope.ref().equals("release/2026.08")
                        && scope.pathPrefixes().equals(List.of("apps/crm-agent"))
        ), argThat(limits -> limits.maxRouteNodes() == 400 && limits.maxContextFiles() == 120));
    }

    @Test
    void shouldExposeSelectedScreenGraphContext() throws Exception {
        when(screenGraphContextService.build(any())).thenReturn(screenContext());

        mockMvc.perform(post("/api/gitlab/frontend/screen-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "synthetic-crm",
                                  "projectName": "crm-agent-portal",
                                  "ref": "release/2026.08",
                                  "pathPrefixes": ["apps/crm-agent"],
                                  "screenId": "screen-crm-customer-profile",
                                  "expectedRevision": "crm-ui-revision-20260815"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenNode.screen.screenId").value("screen-crm-customer-profile"))
                .andExpect(jsonPath("$.sourceManifest[0].roles[0]").value("VIEW_COMPONENT"))
                .andExpect(jsonPath("$.sourceSlices[0].kind").value("COMPONENT_CONTRACT"))
                .andExpect(jsonPath("$.metrics.returnedSliceCount").value(1))
                .andExpect(jsonPath("$.technicalSignals[0].kind").value("REACTIVE_FORM"))
                .andExpect(jsonPath("$.graphCoverage.visitedRouteNodeCount").value(1))
                .andExpect(jsonPath("$.contextLimitReached").value(false));

        verify(screenGraphContextService).build(argThat(request ->
                request.screenId().equals("screen-crm-customer-profile")
                        && request.expectedRevision().equals("crm-ui-revision-20260815")
                        && request.limits().maxContextFiles() == 120
        ));
    }

    @Test
    void shouldRejectUnsafeScopeBeforeCallingGraphServices() throws Exception {
        mockMvc.perform(post("/api/gitlab/frontend/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "synthetic-crm",
                                  "projectName": "crm-agent-portal",
                                  "ref": "",
                                  "pathPrefixes": ["../another-domain"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(routeGraphDiscoveryService, screenGraphContextService);
    }

    @Test
    void shouldMapStaleSyntheticCrmScreenToNotFound() throws Exception {
        when(screenGraphContextService.build(any())).thenThrow(new GitLabFrontendDiscoveryException(
                "FRONTEND_SCREEN_NOT_FOUND", "Synthetic CRM screen is stale"
        ));
        mockMvc.perform(post("/api/gitlab/frontend/screen-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "synthetic-crm",
                                  "projectName": "crm-agent-portal",
                                  "ref": "release/2026.08",
                                  "pathPrefixes": [],
                                  "screenId": "screen-crm-stale"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRONTEND_SCREEN_NOT_FOUND"));
    }

    private GitLabFrontendRouteGraph graph() {
        var node = screenNode();
        return new GitLabFrontendRouteGraph(
                scope(), new GitLabFrontendSourceRevision(scope().ref(), "crm-ui-revision-20260815"),
                mock(GitLabFrontendBootstrapRoot.class), List.of(node.nodeId()), List.of(node), List.of(),
                List.of(chain(node)), List.of(), coverage(), List.of()
        );
    }

    private GitLabFrontendScreenGraphContext screenContext() {
        var node = screenNode();
        var viewPath = node.viewTarget().sourcePath();
        return new GitLabFrontendScreenGraphContext(
                scope(), new GitLabFrontendSourceRevision(scope().ref(), "crm-ui-revision-20260815"),
                node, chain(node), coverage(),
                List.of(new GitLabFrontendSourceManifestEntry(
                        viewPath, List.of(GitLabFrontendSourceRole.VIEW_COMPONENT),
                        44, "crm-profile-sha256", 1
                )),
                List.of(new GitLabFrontendSourceSlice(
                        "frontend-crm-profile", viewPath, List.of(GitLabFrontendSourceRole.VIEW_COMPONENT),
                        GitLabFrontendSourceSliceKind.COMPONENT_CONTRACT, "CrmCustomerProfileComponent",
                        1, 1, "export class CrmCustomerProfileComponent {}", 44, "crm-profile-slice-sha256"
                )),
                List.of(new GitLabFrontendUseCaseRelation(
                        "screen-crm-customer-profile", viewPath, GitLabFrontendUseCaseRelationKind.ROUTE_TO_VIEW,
                        "CrmCustomerProfileComponent", GitLabFrontendSignalConfidence.HIGH,
                        node.routeSource()
                )),
                List.of(),
                List.of(new GitLabFrontendTechnicalSignal(
                        GitLabFrontendTechnicalSignalKind.REACTIVE_FORM,
                        "Synthetic CRM customer form is declared.", GitLabFrontendSignalConfidence.HIGH,
                        new GitLabFrontendSourceReference(viewPath, "CrmCustomerProfileComponent", 10, 40)
                )),
                List.of(new GitLabFrontendContextCoverage(
                        "FORMS", GitLabFrontendCoverageStatus.READY, "Synthetic reactive form source included."
                )), List.of(),
                new GitLabFrontendContextMetrics(1, 44, 1, 44, 0, 0, 1, 0),
                false
        );
    }

    private GitLabFrontendRouteNode screenNode() {
        var routeSource = new GitLabFrontendSourceReference(
                "apps/crm-agent/src/app/app.routes.ts", "crmCustomerRoutes", 12, 22
        );
        var target = new GitLabFrontendRouteTarget(
                "CrmCustomerProfileComponent", "apps/crm-agent/src/app/customer/customer-profile.component.ts"
        );
        var identity = new GitLabFrontendScreenIdentity(
                "screen-crm-customer-profile", "route-crm-customer-profile",
                "/crm/customers/:customerId", "primary", target
        );
        return new GitLabFrontendRouteNode(
                identity.routeNodeId(), null, identity, "Customer profile", "crm/customers/:customerId",
                identity.routePattern(), "primary", GitLabFrontendRouteNodeKind.SCREEN,
                GitLabFrontendDiscoveryStatus.RESOLVED, true, List.of("customerId"), target, null, null,
                List.of(), routeSource, List.of()
        );
    }

    private GitLabFrontendEffectiveRouteChain chain(GitLabFrontendRouteNode node) {
        return new GitLabFrontendEffectiveRouteChain(
                node.screen(), List.of(new GitLabFrontendRouteChainSegment(
                        node.nodeId(), node.pathSegment(), node.routePattern(), node.outlet(), node.configuration(), node.routeSource()
                )), node.routeParameters()
        );
    }

    private GitLabFrontendGraphCoverage coverage() {
        return new GitLabFrontendGraphCoverage(
                GitLabFrontendCoverageStatus.READY, 1, 1, 7, 2, 0, false, List.of()
        );
    }

    private GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "release/2026.08", List.of("apps/crm-agent")
        );
    }
}
