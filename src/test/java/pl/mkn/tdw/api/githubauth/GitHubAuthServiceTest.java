package pl.mkn.tdw.api.githubauth;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubAuthServiceTest {

    @Test
    void statusReflectsWorkspacePatWithoutExposingIt() throws Exception {
        var pat = new AtomicReference<String>();
        var mvc = MockMvcBuilders.standaloneSetup(new GitHubAuthController(new GitHubAuthService(pat::get))).build();

        mvc.perform(get("/api/auth/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.settingsUrl").value("/workspace-settings"))
                .andExpect(jsonPath("$.mode").doesNotExist());

        pat.set("github_pat_crm_operator_token");
        mvc.perform(get("/api/auth/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.settingsUrl").value("/workspace-settings"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }
}
