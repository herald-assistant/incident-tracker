package pl.mkn.tdw.features.configdriftviewer.input;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerSystemOption;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigDriftViewerInputOptionsController.class)
class ConfigDriftViewerInputOptionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigDriftViewerInputOptionsService inputOptionsService;

    @Test
    void shouldExposeSafeInputOptionsWithoutBackendGitLabScope() throws Exception {
        when(inputOptionsService.getOptions()).thenReturn(new ConfigDriftViewerInputOptions(
                List.of(ConfigDriftViewerMode.BASIC, ConfigDriftViewerMode.DEEP),
                List.of("dev1", "zt001"),
                List.of(new ConfigDriftViewerInputOptions.RepositoryOption(
                        "runtime-config",
                        "Runtime configuration"
                )),
                List.of(new ConfigDriftViewerSystemOption("backend", "Backend", "backend"))
        ));

        mockMvc.perform(get("/api/config-drift-viewer/v1/input-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modes[0]").value("BASIC"))
                .andExpect(jsonPath("$.branches[1]").value("zt001"))
                .andExpect(jsonPath("$.repositories[0].id").value("runtime-config"))
                .andExpect(jsonPath("$.systems[0].id").value("backend"))
                .andExpect(jsonPath("$.systems[0].configurationDirectory").value("backend"))
                .andExpect(content().string(not(containsString("connectionId"))))
                .andExpect(content().string(not(containsString("projectPath"))))
                .andExpect(content().string(not(containsString("token"))));
    }
}
