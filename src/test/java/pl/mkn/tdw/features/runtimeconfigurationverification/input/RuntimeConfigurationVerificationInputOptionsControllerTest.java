package pl.mkn.tdw.features.runtimeconfigurationverification.input;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationSystemOption;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuntimeConfigurationVerificationInputOptionsController.class)
class RuntimeConfigurationVerificationInputOptionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeConfigurationVerificationInputOptionsService inputOptionsService;

    @Test
    void shouldExposeSafeInputOptionsWithoutBackendGitLabScope() throws Exception {
        when(inputOptionsService.getOptions()).thenReturn(new RuntimeConfigurationVerificationInputOptions(
                List.of(RuntimeConfigurationVerificationMode.BASIC, RuntimeConfigurationVerificationMode.DEEP),
                List.of("dev1", "zt001"),
                List.of(new RuntimeConfigurationVerificationInputOptions.RepositoryOption(
                        "runtime-config",
                        "Runtime configuration"
                )),
                List.of(new RuntimeConfigurationSystemOption("backend", "Backend", "backend"))
        ));

        mockMvc.perform(get("/api/runtime-configuration-verification/input-options"))
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
