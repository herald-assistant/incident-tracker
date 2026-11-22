package pl.mkn.tdw.features.systemassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.agenttools.openapi.mcp.OpenApiMcpEndpoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "system-assistant.enabled=false")
@AutoConfigureMockMvc
class SystemAssistantDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Test void exposesNoAssistantApiOrMcpWhenDisabled() throws Exception {
        assertThat(context.getBeansOfType(OpenApiMcpEndpoint.class)).isEmpty();
        mvc.perform(get("/api/system-assistant/configuration")).andExpect(status().isNotFound());
        mvc.perform(post("/api/openapi-tools/test_profile/mcp").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ui/config")).andExpect(jsonPath("$.systemAssistantEnabled").value(false));
        mvc.perform(get("/browser-tools/availability.js"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("systemAssistantEnabled:false")));
    }
}
