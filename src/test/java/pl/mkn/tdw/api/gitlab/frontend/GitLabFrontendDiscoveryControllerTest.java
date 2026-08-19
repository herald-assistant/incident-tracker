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

    @MockitoBean
    private GitLabAngularRouteBranchSliceService routeBranchSliceService;

    @MockitoBean
    private GitLabTypeScriptSymbolSliceService typeScriptSymbolSliceService;

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
                .andExpect(jsonPath("$.sourceFiles[0].roles[0]").value("VIEW_COMPONENT"))
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
    void shouldExposeSyntheticCrmAngularRouteBranchSlice() throws Exception {
        var node = screenNode();
        when(routeBranchSliceService.readBranchSlice(any())).thenReturn(
                new GitLabAngularRouteBranchSliceResponse(
                        scope(), new GitLabFrontendSourceRevision(scope().ref(), "crm-ui-revision-20260815"),
                        "OK", node, chain(node),
                        List.of(new GitLabAngularRouteBranchSliceFile(
                                node.routeSource().path(), "{ path: 'customers/:customerId' }",
                                8_000, 39, List.of(node.nodeId()), List.of(), 6, 4, false
                        )),
                        List.of(), 8_000, 39, 7_961, 6, 4, false, List.of(), List.of()
                )
        );

        mockMvc.perform(post("/api/gitlab/frontend/route-branch-slice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "synthetic-crm",
                                  "projectName": "crm-agent-portal",
                                  "ref": "release/2026.08",
                                  "pathPrefixes": ["apps/crm-agent"],
                                  "screenId": "screen-crm-customer-profile",
                                  "expectedRevision": "crm-ui-revision-20260815",
                                  "includeDescendantRoutes": false,
                                  "maxCharacters": 24000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.savedCharacters").value(7961))
                .andExpect(jsonPath("$.files[0].omittedSiblingRouteCount").value(4));

        verify(routeBranchSliceService).readBranchSlice(argThat(request ->
                request.screenId().equals("screen-crm-customer-profile")
                        && request.expectedRevision().equals("crm-ui-revision-20260815")
                        && request.maxCharacters() == 24_000
                        && !Boolean.TRUE.equals(request.includeDescendantRoutes())
        ));
    }

    @Test
    void shouldExposeSyntheticCrmTypeScriptSymbolSlice() throws Exception {
        when(typeScriptSymbolSliceService.readSymbolSlice(any())).thenReturn(
                new GitLabTypeScriptSymbolSliceResponse(
                        scope(), "apps/crm-agent/src/app/customer/crm-customer-editor.component.ts",
                        "OK", "CrmCustomerEditorComponent", 80, 112, 300, 18_000,
                        "saveCustomer(): void {}", 24, 17_976, false, List.of(), List.of("customerApi"),
                        List.of(new GitLabTypeScriptSymbolCandidate(
                                "CrmCustomerEditorComponent", "saveCustomer", GitLabTypeScriptSymbolKind.METHOD,
                                "saveCustomer(): void", 80, 112
                        )),
                        9, 12, 17, List.of(), List.of(), List.of()
                )
        );

        mockMvc.perform(post("/api/gitlab/frontend/typescript-symbol-slice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "synthetic-crm",
                                  "projectName": "crm-agent-portal",
                                  "ref": "release/2026.08",
                                  "pathPrefixes": ["apps/crm-agent"],
                                  "filePath": "apps/crm-agent/src/app/customer/crm-customer-editor.component.ts",
                                  "declaringTypeName": "CrmCustomerEditorComponent",
                                  "symbolSelectors": [{"name": "saveCustomer", "kind": "METHOD", "lineStart": 80}],
                                  "includeLocalHelpers": true,
                                  "includeRelevantFields": true,
                                  "includeRelevantImports": true,
                                  "maxCharacters": 12000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.includedSymbols[0].symbolName").value("saveCustomer"))
                .andExpect(jsonPath("$.savedCharacters").value(17976));

        verify(typeScriptSymbolSliceService).readSymbolSlice(argThat(request ->
                request.filePath().endsWith("crm-customer-editor.component.ts")
                        && request.symbolSelectors().get(0).kind() == GitLabTypeScriptSymbolKind.METHOD
                        && request.maxCharacters() == 12_000
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
        verifyNoInteractions(
                routeGraphDiscoveryService, screenGraphContextService,
                routeBranchSliceService, typeScriptSymbolSliceService
        );
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
                List.of(new GitLabFrontendSourceFile(
                        viewPath, List.of(GitLabFrontendSourceRole.VIEW_COMPONENT),
                        "export class CrmCustomerProfileComponent {}", 44, false
                )),
                List.of(new GitLabFrontendTechnicalSignal(
                        GitLabFrontendTechnicalSignalKind.REACTIVE_FORM,
                        "Synthetic CRM customer form is declared.", GitLabFrontendSignalConfidence.HIGH,
                        new GitLabFrontendSourceReference(viewPath, "CrmCustomerProfileComponent", 10, 40)
                )),
                List.of(new GitLabFrontendContextCoverage(
                        "FORMS", GitLabFrontendCoverageStatus.READY, "Synthetic reactive form source included."
                )), List.of(), 44, false
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
