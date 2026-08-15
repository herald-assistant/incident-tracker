package pl.mkn.tdw.features.uiexplorer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerScreenCatalog;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerScreenCatalogBoundary;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerScreenCatalogEntry;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerScreenCatalogService;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerScreenCatalogStatus;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiExplorerScreenCatalogController.class)
class UiExplorerScreenCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UiExplorerScreenCatalogService screenCatalogService;

    @Test
    void shouldExposeBusinessCatalogWithoutRepositoryScope() throws Exception {
        when(screenCatalogService.loadCatalog("crm-agent-portal", "release/2026.08"))
                .thenReturn(catalog());

        mockMvc.perform(get("/api/ui-explorer/screens")
                        .queryParam("systemId", "crm-agent-portal")
                        .queryParam("branch", "release/2026.08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemId").value("crm-agent-portal"))
                .andExpect(jsonPath("$.systemLabel").value("CRM Agent Portal"))
                .andExpect(jsonPath("$.sourceRevision.revision").value("crm-ui-revision-20260815"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.screens[0].screenId").value("crm-customer-profile"))
                .andExpect(jsonPath("$.screens[0].routePattern").value("/crm/customers/:customerId"))
                .andExpect(jsonPath("$.boundary.maxInventoryFiles").value(2_000))
                .andExpect(jsonPath("$.repositoryId").doesNotExist())
                .andExpect(jsonPath("$.projectPath").doesNotExist())
                .andExpect(jsonPath("$.gitLabGroup").doesNotExist());

        verify(screenCatalogService).loadCatalog("crm-agent-portal", "release/2026.08");
    }

    @Test
    void shouldRequireBranchQueryParameter() throws Exception {
        mockMvc.perform(get("/api/ui-explorer/screens")
                        .queryParam("systemId", "crm-agent-portal"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExposeIneligibleFrontendAsPublicError() throws Exception {
        when(screenCatalogService.loadCatalog("crm-agent-portal", "main"))
                .thenThrow(new UiExplorerFrontendNotEligibleException("crm-agent-portal"));

        mockMvc.perform(get("/api/ui-explorer/screens")
                        .queryParam("systemId", "crm-agent-portal")
                        .queryParam("branch", "main"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UI_EXPLORER_FRONTEND_NOT_ELIGIBLE"));
    }

    private UiExplorerScreenCatalog catalog() {
        return new UiExplorerScreenCatalog(
                "crm-agent-portal",
                "CRM Agent Portal",
                new UiExplorerSourceRevision("release/2026.08", "crm-ui-revision-20260815"),
                UiExplorerScreenCatalogStatus.READY,
                List.of(new UiExplorerScreenCatalogEntry(
                        "crm-customer-profile",
                        "Customer profile",
                        "/crm/customers/:customerId",
                        "/crm/customers",
                        "RESOLVED",
                        true,
                        List.of("CrmAgentGuard"),
                        List.of("customerId"),
                        List.of()
                )),
                List.of(),
                List.of(),
                new UiExplorerScreenCatalogBoundary(42, 1, false, false, 2_000, 80, 400)
        );
    }
}
