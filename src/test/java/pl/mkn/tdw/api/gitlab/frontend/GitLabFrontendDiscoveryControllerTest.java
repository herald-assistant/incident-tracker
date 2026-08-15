package pl.mkn.tdw.api.gitlab.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendContextCoverage;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendCoverageStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnostic;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnosticSeverity;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteCatalog;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteCatalogRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteEntry;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteEntryKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenContextRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenSourceContext;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSignalConfidence;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceFile;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceReference;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceRole;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendTechnicalSignal;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendTechnicalSignalKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendWorkspaceSignal;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitLabFrontendDiscoveryController.class)
class GitLabFrontendDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitLabFrontendSourceDiscoveryService gitLabFrontendSourceDiscoveryService;

    @Test
    void shouldExposeBoundedFrontendCatalogForCrmRepository() throws Exception {
        when(gitLabFrontendSourceDiscoveryService.discoverCatalog(
                org.mockito.ArgumentMatchers.any(GitLabFrontendRouteCatalogRequest.class)
        )).thenReturn(catalog());

        mockMvc.perform(post("/api/gitlab/frontend/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM/apps",
                                  "projectName": "crm-agent-portal",
                                  "ref": "release/2026.08",
                                  "pathPrefixes": ["apps/crm-agent"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRevision.commitId").value("crm-ui-revision-20260815"))
                .andExpect(jsonPath("$.entries[0].screenId").value("crm-customer-profile"))
                .andExpect(jsonPath("$.entries[0].routePattern").value("/crm/customers/:customerId"))
                .andExpect(jsonPath("$.workspaceSignals[0].kind").value("FRAMEWORK"))
                .andExpect(jsonPath("$.inventoryTruncated").value(false));

        verify(gitLabFrontendSourceDiscoveryService).discoverCatalog(argThat(request ->
                request.scope().group().equals("CRM/apps")
                        && request.scope().projectName().equals("crm-agent-portal")
                        && request.scope().ref().equals("release/2026.08")
                        && request.scope().pathPrefixes().equals(List.of("apps/crm-agent"))
                        && request.limits().maxInventoryFiles() == 2_000
                        && request.limits().maxTotalCharacters() == 500_000
        ));
    }

    @Test
    void shouldExposeScreenSourceContextForCatalogScreen() throws Exception {
        when(gitLabFrontendSourceDiscoveryService.buildScreenContext(
                org.mockito.ArgumentMatchers.any(GitLabFrontendScreenContextRequest.class)
        )).thenReturn(screenContext());

        mockMvc.perform(post("/api/gitlab/frontend/screen-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM/apps",
                                  "projectName": "crm-agent-portal",
                                  "ref": "release/2026.08",
                                  "pathPrefixes": ["apps/crm-agent"],
                                  "screenId": "crm-customer-profile"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.screenId").value("crm-customer-profile"))
                .andExpect(jsonPath("$.sourceFiles[0].roles[0]").value("VIEW_COMPONENT"))
                .andExpect(jsonPath("$.technicalSignals[0].kind").value("REACTIVE_FORM"))
                .andExpect(jsonPath("$.coverage[0].status").value("READY"))
                .andExpect(jsonPath("$.totalReturnedCharacters").value(48));

        verify(gitLabFrontendSourceDiscoveryService).buildScreenContext(argThat(request ->
                request.screenId().equals("crm-customer-profile")
                        && request.scope().group().equals("CRM/apps")
                        && request.limits().maxContextFiles() == 40
        ));
    }

    @Test
    void shouldRejectUnboundedOrUnsafeCatalogScope() throws Exception {
        mockMvc.perform(post("/api/gitlab/frontend/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM/apps",
                                  "projectName": "crm-agent-portal",
                                  "ref": "",
                                  "pathPrefixes": ["../another-domain"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(gitLabFrontendSourceDiscoveryService);
    }

    @Test
    void shouldMapStaleCrmScreenToNotFound() throws Exception {
        when(gitLabFrontendSourceDiscoveryService.buildScreenContext(
                org.mockito.ArgumentMatchers.any(GitLabFrontendScreenContextRequest.class)
        )).thenThrow(new GitLabFrontendDiscoveryException(
                "FRONTEND_SCREEN_NOT_FOUND",
                "screenId does not belong to the current repository/ref catalog"
        ));

        mockMvc.perform(post("/api/gitlab/frontend/screen-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM/apps",
                                  "projectName": "crm-agent-portal",
                                  "ref": "release/2026.08",
                                  "pathPrefixes": [],
                                  "screenId": "crm-stale-screen"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRONTEND_SCREEN_NOT_FOUND"));
    }

    private GitLabFrontendRouteCatalog catalog() {
        return new GitLabFrontendRouteCatalog(
                scope(),
                new GitLabFrontendSourceRevision("release/2026.08", "crm-ui-revision-20260815"),
                List.of(new GitLabFrontendWorkspaceSignal(
                        "FRAMEWORK", "Angular 20", "apps/crm-agent/project.json"
                )),
                List.of(screen()),
                List.of(new GitLabFrontendDiagnostic(
                        GitLabFrontendDiagnosticSeverity.INFO,
                        "CRM_ROUTE_CATALOG_READY",
                        "Static CRM route catalog discovered.",
                        "apps/crm-agent/src/app/app.routes.ts"
                )),
                42,
                1,
                false,
                false
        );
    }

    private GitLabFrontendScreenSourceContext screenContext() {
        var source = new GitLabFrontendSourceReference(
                "apps/crm-agent/src/app/customer/customer-profile.ts",
                "CrmCustomerProfileComponent",
                10,
                80
        );
        return new GitLabFrontendScreenSourceContext(
                scope(),
                new GitLabFrontendSourceRevision("release/2026.08", "crm-ui-revision-20260815"),
                screen(),
                List.of(new GitLabFrontendWorkspaceSignal(
                        "FRAMEWORK", "Angular 20", "apps/crm-agent/project.json"
                )),
                List.of(new GitLabFrontendSourceFile(
                        source.path(),
                        List.of(GitLabFrontendSourceRole.VIEW_COMPONENT),
                        "export class CrmCustomerProfileComponent {}",
                        48,
                        false
                )),
                List.of(new GitLabFrontendTechnicalSignal(
                        GitLabFrontendTechnicalSignalKind.REACTIVE_FORM,
                        "CRM customer profile form is declared in the component.",
                        GitLabFrontendSignalConfidence.HIGH,
                        source
                )),
                List.of(new GitLabFrontendContextCoverage(
                        "FORMS", GitLabFrontendCoverageStatus.READY, "Reactive form source included."
                )),
                List.of(),
                42,
                1,
                false,
                false,
                48,
                false
        );
    }

    private GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope(
                "CRM/apps", "crm-agent-portal", "release/2026.08", List.of("apps/crm-agent")
        );
    }

    private GitLabFrontendRouteEntry screen() {
        return new GitLabFrontendRouteEntry(
                "crm-customer-profile",
                "Customer profile",
                "/crm/customers/:customerId",
                "/crm/customers",
                GitLabFrontendRouteEntryKind.SCREEN,
                GitLabFrontendDiscoveryStatus.RESOLVED,
                true,
                List.of("CrmAgentGuard"),
                List.of("customerId"),
                null,
                "CrmCustomerProfileComponent",
                "apps/crm-agent/src/app/customer/customer-profile.ts",
                new GitLabFrontendSourceReference(
                        "apps/crm-agent/src/app/app.routes.ts", "crmCustomerRoutes", 12, 22
                ),
                List.of()
        );
    }
}
