package pl.mkn.tdw.features.runtimeconfigurationverification.deep;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflight;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflightStatus;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuntimeConfigurationDeepPreflightController.class)
class RuntimeConfigurationDeepPreflightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeConfigurationDeepPreflightService preflightService;

    @Test
    void shouldExposeSafeDeepPreflight() throws Exception {
        when(preflightService.check("runtime-config", "backend", "release-42"))
                .thenReturn(new RuntimeConfigurationDeepPreflight(
                        RuntimeConfigurationDeepPreflightStatus.READY,
                        "runtime-config",
                        "backend",
                        "Backend",
                        "backend",
                        List.of(),
                        List.of(),
                        List.of("Requested ref is not deployment evidence.")
                ));

        mockMvc.perform(get("/api/runtime-configuration-verification/deep-preflight")
                        .queryParam("repositoryId", "runtime-config")
                        .queryParam("systemId", "backend")
                        .queryParam("codeRef", "release-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.systemId").value("backend"))
                .andExpect(jsonPath("$.resolvedConfigurationDirectory").value("backend"))
                .andExpect(content().string(not(containsString("connectionId"))))
                .andExpect(content().string(not(containsString("token"))))
                .andExpect(content().string(not(containsString("baseUrl"))));
    }
}
