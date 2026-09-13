package pl.mkn.tdw.features.operationalcontextassistance.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceOptionsService;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalContextAssistanceSourceOptionsController.class)
class OperationalContextAssistanceSourceOptionsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OperationalContextGitLabSourceOptionsService sourceOptionsService;

    @Test
    void returnsConfiguredGroupAndUnambiguousCatalogSuggestions() throws Exception {
        when(sourceOptionsService.getOptions()).thenReturn(new OperationalContextAssistanceSourceOptions(
                "https://gitlab.example.com",
                "CRM/runtime",
                List.of(new OperationalContextAssistanceSourceOptions.Project(
                        "libs/customer-api", "CRM/runtime/libs/customer-api"
                ))
        ));

        mockMvc.perform(get("/api/operational-context/assistance/source-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredBaseUrl").value("https://gitlab.example.com"))
                .andExpect(jsonPath("$.configuredGroup").value("CRM/runtime"))
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].project").value("libs/customer-api"))
                .andExpect(jsonPath("$.projects[0].projectPath").value("CRM/runtime/libs/customer-api"));
    }

    @Test
    void returnsEmptySuggestionsWithoutConfiguredGroup() throws Exception {
        when(sourceOptionsService.getOptions()).thenReturn(new OperationalContextAssistanceSourceOptions(
                null, null, List.of()));

        mockMvc.perform(get("/api/operational-context/assistance/source-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredGroup").value(nullValue()))
                .andExpect(jsonPath("$.projects").isEmpty());
    }
}
