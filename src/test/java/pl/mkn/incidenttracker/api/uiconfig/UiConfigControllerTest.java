package pl.mkn.incidenttracker.api.uiconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiConfigController.class)
class UiConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UiConfigService uiConfigService;

    @Test
    void shouldExposeUiConfig() throws Exception {
        when(uiConfigService.currentConfig()).thenReturn(new UiConfigResponse(
                "Acme Engineering Workspace",
                "Team Delivery Workspace",
                "Team Delivery Workspace"
        ));

        mockMvc.perform(get("/api/ui/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Acme Engineering Workspace"))
                .andExpect(jsonPath("$.subtitle").value("Team Delivery Workspace"))
                .andExpect(jsonPath("$.defaultTitle").value("Team Delivery Workspace"));
    }
}
