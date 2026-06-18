package pl.mkn.incidenttracker.features.flowexplorer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.features.flowexplorer.FlowExplorerProperties;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlowExplorerConfigController.class)
class FlowExplorerConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlowExplorerProperties flowExplorerProperties;

    @Test
    void shouldReturnConfiguredDefaults() throws Exception {
        when(flowExplorerProperties.getDefaultBranch()).thenReturn("release-candidate");

        mockMvc.perform(get("/flow-explorer/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultBranch").value("release-candidate"));
    }
}
