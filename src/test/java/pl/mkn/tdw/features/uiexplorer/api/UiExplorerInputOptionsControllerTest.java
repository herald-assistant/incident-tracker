package pl.mkn.tdw.features.uiexplorer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerProfile;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailability;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailabilityStatus;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiExplorerInputOptionsController.class)
class UiExplorerInputOptionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UiExplorerInputOptionsService inputOptionsService;

    @Test
    void shouldExposeInputOptionsWithoutRepositoryScopeInSystemOption() throws Exception {
        when(inputOptionsService.inputOptions()).thenReturn(new UiExplorerInputOptionsResponse(
                "ui-explorer",
                new UiExplorerOutputAvailability(
                        UiExplorerOutputAvailabilityStatus.AVAILABLE,
                        "UI_EXPLORER_ANALYSIS_AVAILABLE",
                        "Screen catalog, bounded source context and AI analysis are available.",
                        List.of()
                ),
                List.of(new UiExplorerInputOptionsResponse.SystemOption(
                        "crm-agent-portal",
                        "CRM Agent Portal",
                        "Strongly anonymized CRM frontend."
                )),
                List.of(new UiExplorerInputOptionsResponse.ProfileOption(
                        UiExplorerProfile.FUNCTIONAL_DOCUMENTATION,
                        "Dokumentacja funkcjonalna",
                        "Synthetic CRM functional documentation.",
                        List.of(new UiExplorerSectionModeAssignment(
                                UiExplorerSectionId.OVERVIEW,
                                UiExplorerSectionMode.DEEP
                        ))
                )),
                List.of(new UiExplorerInputOptionsResponse.SectionOption(
                        UiExplorerSectionId.OVERVIEW,
                        "Cel i kontekst widoku",
                        "Synthetic CRM overview."
                )),
                List.of(new UiExplorerInputOptionsResponse.ModeOption(
                        UiExplorerSectionMode.DEEP,
                        "Poglebiona",
                        "Deep synthetic CRM analysis."
                )),
                List.of()
        ));

        mockMvc.perform(get("/api/ui-explorer/input-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureId").value("ui-explorer"))
                .andExpect(jsonPath("$.executionAvailability.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.systems[0].systemId").value("crm-agent-portal"))
                .andExpect(jsonPath("$.systems[0].repositoryId").doesNotExist())
                .andExpect(jsonPath("$.profiles[0].profile").value("FUNCTIONAL_DOCUMENTATION"))
                .andExpect(jsonPath("$.sections[0].sectionId").value("OVERVIEW"))
                .andExpect(jsonPath("$.modes[0].mode").value("DEEP"));
    }
}
