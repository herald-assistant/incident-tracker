package pl.mkn.tdw.features.uxinspector.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorContextException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UxInspectorInputOptionsController.class)
class UxInspectorInputOptionsControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean UxInspectorInputOptionsService service;

    @Test
    void shouldExposeTrustedInputOptionsAndPinnedRevision() throws Exception {
        when(service.inputOptions()).thenReturn(new UxInspectorInputOptionsResponse("ux-inspector",
                List.of(new UxInspectorInputOptionsResponse.SystemOption(
                        "crm-agent-portal", "CRM Agent Portal", "Fikcyjny frontend CRM", "main")), List.of()));
        when(service.views("crm-agent-portal", "main", false)).thenReturn(new UxInspectorViewCatalogResponse(
                "crm-agent-portal", "CRM Agent Portal",
                new UxInspectorViewCatalogResponse.SourceRevision("main", "abc123crm"), "READY",
                List.of(new UxInspectorViewCatalogResponse.ViewOption(
                        "crm-contact-create", "Nowy kontakt", "/contacts/new",
                        List.of("crm-contact-create"), "RESOLVED", List.of())),
                List.of(), List.of()));

        mockMvc.perform(get("/api/ux-inspector/input-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureId").value("ux-inspector"))
                .andExpect(jsonPath("$.systems[0].systemId").value("crm-agent-portal"));
        mockMvc.perform(get("/api/ux-inspector/views")
                        .param("systemId", "crm-agent-portal").param("branch", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRevision.revision").value("abc123crm"))
                .andExpect(jsonPath("$.views[0].viewId").value("crm-contact-create"))
                .andExpect(jsonPath("$.views[0].componentSelectors[0]").value("crm-contact-create"));

        when(service.views("crm-agent-portal", "main", true)).thenReturn(new UxInspectorViewCatalogResponse(
                "crm-agent-portal", "CRM Agent Portal",
                new UxInspectorViewCatalogResponse.SourceRevision("main", "fresh456crm"), "READY",
                List.of(), List.of(), List.of()));
        mockMvc.perform(get("/api/ux-inspector/views")
                        .param("systemId", "crm-agent-portal").param("branch", "main")
                        .param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRevision.revision").value("fresh456crm"));
    }

    @Test
    void shouldMapUnknownFrontendAndStaleRefToPublicErrors() throws Exception {
        when(service.views("unknown-crm", "main", false)).thenThrow(new UxInspectorContextException(
                "UX_INSPECTOR_FRONTEND_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                "Selected frontend is not registered for source analysis."));
        when(service.views("crm-agent-portal", "stale", false)).thenThrow(new UxInspectorContextException(
                "UX_INSPECTOR_SOURCE_REVISION_CHANGED", UserFacingErrorType.CONFLICT,
                "Source revision changed. Reload views and select the target again."));

        mockMvc.perform(get("/api/ux-inspector/views")
                        .param("systemId", "unknown-crm").param("branch", "main"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UX_INSPECTOR_FRONTEND_NOT_FOUND"));
        mockMvc.perform(get("/api/ux-inspector/views")
                        .param("systemId", "crm-agent-portal").param("branch", "stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UX_INSPECTOR_SOURCE_REVISION_CHANGED"));
    }
}
