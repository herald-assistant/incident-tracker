package pl.mkn.tdw.features.flowexplorer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSystemSelectionService;

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
                "catalog-core",
                "Catalog Core",
                "Catalog",
                "internal-application",
                "active",
                "healthy",
                "high",
                "Handles catalog operations.",
                List.of("catalog"),
                2,
                1,
                List.of("catalog-team")
        )));

        mockMvc.perform(get("/api/flow-explorer/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].systemId").value("catalog-core"))
                .andExpect(jsonPath("$[0].name").value("Catalog Core"))
                .andExpect(jsonPath("$[0].kind").value("internal-application"))
                .andExpect(jsonPath("$[0].repositoryCount").value(2))
                .andExpect(jsonPath("$[0].codeSearchScopeCount").value(1))
                .andExpect(jsonPath("$[0].ownerTeamIds[0]").value("catalog-team"));

        verify(flowExplorerSystemSelectionService).systems();
    }
}
