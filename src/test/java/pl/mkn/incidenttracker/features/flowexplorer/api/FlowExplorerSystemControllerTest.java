package pl.mkn.incidenttracker.features.flowexplorer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSystemSelectionService;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlowExplorerSystemController.class)
class FlowExplorerSystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlowExplorerSystemSelectionService flowExplorerSystemSelectionService;

    @Test
    void shouldReturnFlowExplorerSystemOptions() throws Exception {
        when(flowExplorerSystemSelectionService.systems()).thenReturn(List.of(new FlowExplorerSystemOptionResponse(
                "billing-core",
                "Billing Core",
                "Billing",
                "internal-application",
                "active",
                "healthy",
                "high",
                "Handles billing operations.",
                List.of("billing"),
                2,
                1,
                List.of("billing-team")
        )));

        mockMvc.perform(get("/api/flow-explorer/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].systemId").value("billing-core"))
                .andExpect(jsonPath("$[0].name").value("Billing Core"))
                .andExpect(jsonPath("$[0].kind").value("internal-application"))
                .andExpect(jsonPath("$[0].repositoryCount").value(2))
                .andExpect(jsonPath("$[0].codeSearchScopeCount").value(1))
                .andExpect(jsonPath("$[0].ownerTeamIds[0]").value("billing-team"));

        verify(flowExplorerSystemSelectionService).systems();
    }
}
