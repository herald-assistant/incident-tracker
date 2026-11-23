package pl.mkn.tdw.features.systemassistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.mkn.tdw.features.systemassistant.api.SystemAssistantAuthController;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.integrations.httpauth.OidcLoginPort;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemAssistantAuthControllerTest {
    @Test void returnsToOnlyTheBoundConversationAndProfileWithoutAUserSuppliedReturnUrl() throws Exception {
        var profile = new HttpAuthProfile("crm", "CRM", "OIDC", null, null, null, null, "crm-client", null,
                "openid", "https://sso.example.com/issuer", "http://localhost/api/system-assistant/auth/callback", "none");
        var configuration = SystemAssistantTestSupport.configuration((id, yaml) -> List.of());
        configuration.save(new SystemAssistantConfiguration(List.of(), List.of(),
                List.of(new SystemAssistantConfiguration.Profile("crm_profile", "CRM", "*", List.of(), List.of())), List.of(profile)));
        var oidc = mock(OidcLoginPort.class);
        when(oidc.begin(eq(profile), anyString())).thenReturn("https://sso.example.com/authorize?state=first",
                "https://sso.example.com/authorize?state=denied", "https://sso.example.com/authorize?state=other");
        var mvc = MockMvcBuilders.standaloneSetup(new SystemAssistantAuthController(configuration, oidc))
                .setControllerAdvice(new pl.mkn.tdw.api.ApiExceptionHandler()).build();
        var cookie = new jakarta.servlet.http.Cookie("tdw_oidc_browser", "a".repeat(43));
        var body = "{\"conversationId\":\"crm-chat\",\"profileId\":\"crm_profile\"}";
        mvc.perform(post("/api/system-assistant/auth/crm/login").cookie(cookie).header("Origin", "http://localhost")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/system-assistant/auth/callback").cookie(cookie).param("state", "first").param("code", "test-code"))
                .andExpect(redirectedUrl("/system-assistant?auth=success&localRunId=crm-chat&profileId=crm_profile"));
        doThrow(new IllegalArgumentException("provider denied")).when(oidc).complete(anyList(), any(), any(), any(), any(), any());
        mvc.perform(get("/api/system-assistant/auth/callback").cookie(cookie).param("state", "first"))
                .andExpect(redirectedUrl("/system-assistant?auth=failed"));
        mvc.perform(post("/api/system-assistant/auth/crm/login").cookie(cookie).header("Origin", "http://localhost")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/system-assistant/auth/callback").cookie(cookie).param("state", "denied").param("error", "access_denied"))
                .andExpect(redirectedUrl("/system-assistant?auth=failed&localRunId=crm-chat&profileId=crm_profile"));
        mvc.perform(post("/api/system-assistant/auth/crm/login").cookie(cookie).header("Origin", "http://localhost")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/system-assistant/auth/callback").param("state", "other"))
                .andExpect(redirectedUrl("/system-assistant?auth=failed"));
        mvc.perform(post("/api/system-assistant/auth/crm/login").cookie(cookie).header("Origin", "http://localhost")
                .contentType(MediaType.APPLICATION_JSON).content("{\"conversationId\":\"//evil.example\",\"profileId\":\"crm_profile\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void bindsLoginToBrowserReturnsOnlyAuthorizationUrlAndUsesFixedReturnLocation() throws Exception {
        var profile = new HttpAuthProfile("crm", "CRM", "OIDC", null, null, null, null, "crm-client", null,
                "openid", "https://sso.example.com/issuer", "http://localhost/api/system-assistant/auth/callback", "none");
        var configuration = SystemAssistantTestSupport.configuration((id, yaml) -> List.of());
        configuration.save(new SystemAssistantConfiguration(List.of(), List.of(), List.of(), List.of(profile)));
        var oidc = mock(OidcLoginPort.class);
        when(oidc.begin(eq(profile), anyString())).thenReturn("https://sso.example.com/authorize?state=opaque");
        var mvc = MockMvcBuilders.standaloneSetup(new SystemAssistantAuthController(configuration, oidc))
                .setControllerAdvice(new pl.mkn.tdw.api.ApiExceptionHandler()).build();
        var response = mvc.perform(post("/api/system-assistant/auth/crm/login").header("Origin", "http://localhost")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorizationUrl").value("https://sso.example.com/authorize?state=opaque"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax"))).andReturn();
        var cookie = response.getResponse().getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
        mvc.perform(get("/api/system-assistant/auth/callback").param("state", "opaque").param("code", "test-code")
                .cookie(new jakarta.servlet.http.Cookie("tdw_oidc_browser", cookie)))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/system-assistant?auth=success"));
        verify(oidc).complete(List.of(profile), "opaque", "test-code", null, cookie, profile.redirectUri());
        doThrow(new IllegalArgumentException("untrusted provider error")).when(oidc).complete(anyList(), any(), any(), any(), any(), any());
        mvc.perform(get("/api/system-assistant/auth/callback").param("state", "opaque"))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/system-assistant?auth=failed"));
        mvc.perform(post("/api/system-assistant/auth/crm/login").header("Origin", "http://other.example.com")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/system-assistant/auth/crm/disconnect").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        verify(oidc).disconnect(profile);
    }
}
