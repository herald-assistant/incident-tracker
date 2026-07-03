package pl.mkn.tdw.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeLandingPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Team Delivery Workspace")))
                .andExpect(content().string(containsString("<html lang=\"pl\"")))
                .andExpect(content().string(containsString("<app-root>")))
                .andExpect(content().string(containsString("main-")))
                .andExpect(content().string(containsString("styles-")));
    }

    @Test
    void shouldServeIncidentAnalysisRoute() throws Exception {
        mockMvc.perform(get("/incident-analysis"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldServeAnalysisHistoryRoute() throws Exception {
        mockMvc.perform(get("/analysis-history"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/analysis-history/analysis-1"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldServeWorkspaceSettingsRoute() throws Exception {
        mockMvc.perform(get("/workspace-settings"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/workspace-settings/gitlab"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldServeIntegrationRoutes() throws Exception {
        mockMvc.perform(get("/evidence"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/elastic"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/gitlab"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldServeDatabaseRoute() throws Exception {
        mockMvc.perform(get("/database"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldServeFlowExplorerRoute() throws Exception {
        mockMvc.perform(get("/flow-explorer"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/flow-explorer/customer-onboarding"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldServeAnyFutureFrontendRouteWithoutControllerUpdates() throws Exception {
        mockMvc.perform(get("/future-feature"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/future-feature/nested-view"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/analysis/future-feature"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldNotForwardBackendApisOrStaticFilesToFrontend() throws Exception {
        mockMvc.perform(get("/api/missing-route"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/analysis/jobs/missing-route"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/missing-bundle.js"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/assets/brand/main-logo.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    @Test
    void shouldNotForwardMcpInfrastructureEndpointsToFrontend() throws Exception {
        mockMvc.perform(get("/mcp"))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertNull(result.getResponse().getForwardedUrl()));

        mockMvc.perform(get("/sse"))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertNull(result.getResponse().getForwardedUrl()));
    }

}
