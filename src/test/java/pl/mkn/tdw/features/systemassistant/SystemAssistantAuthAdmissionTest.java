package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.mkn.tdw.features.systemassistant.ai.SystemAssistantAiPort;
import pl.mkn.tdw.features.systemassistant.api.SystemAssistantConversationController;
import pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationService;
import pl.mkn.tdw.features.systemassistant.contract.*;
import pl.mkn.tdw.integrations.httpauth.OidcLoginPort;
import pl.mkn.tdw.integrations.httpauth.contract.*;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard;

import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemAssistantAuthAdmissionTest {
    @TempDir Path workspace;

    @Test void rejectsBeforeAcceptanceThenAllowsManualResendOnceAuthenticationIsReady() throws Exception {
        var configuration = SystemAssistantTestSupport.configuration(new OpenApiImporter(new ObjectMapper()));
        var auth = new HttpAuthProfile("crm_oidc", "CRM login", "OIDC", null, null, null, null,
                "crm-client", null, "openid", "https://sso.example.com", "http://localhost/api/system-assistant/auth/callback", "none");
        configuration.save(new SystemAssistantConfiguration(List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "CRM",
                Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml")))), List.of(),
                List.of(new SystemAssistantConfiguration.Profile("crm", "CRM", "*",
                        List.of(new SystemAssistantConfiguration.ApiBinding("crm", auth.id())), List.of()),
                        new SystemAssistantConfiguration.Profile("missing", "Missing CRM", "*",
                                List.of(new SystemAssistantConfiguration.ApiBinding("deleted", auth.id())), List.of())), List.of(auth)));
        var oidc = mock(OidcLoginPort.class);
        when(oidc.authorization(auth)).thenThrow(new HttpAuthException("Login required")).thenReturn("Bearer test-token");
        var ai = mock(SystemAssistantAiPort.class);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any())).thenReturn(new SystemAssistantAiPort.Reply("Gotowe", null));
        var service = new SystemAssistantConversationService(configuration, ai, SystemAssistantTestSupport.history(workspace),
                new LocalAnalysisRunOperationGuard(), oidc);
        try {
            var id = service.create(null).id();
            var mvc = MockMvcBuilders.standaloneSetup(new SystemAssistantConversationController(service))
                    .setControllerAdvice(new pl.mkn.tdw.api.ApiExceptionHandler()).build();
            mvc.perform(post("/api/system-assistant/conversations/{id}/messages", id).contentType("application/json")
                            .content("{\"message\":\"Sprawdź klienta\",\"profileId\":\"crm\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH_LOGIN_REQUIRED"))
                    .andExpect(jsonPath("$.authProfileId").value("crm_oidc"))
                    .andExpect(header().string("Cache-Control", "no-store"));
            assertThat(service.get(id).messages()).isEmpty();
            assertThat(service.get(id).busy()).isFalse();
            verify(ai, never()).answer(any(), any(), any(), any(), any(), any(), any());
            service.send(id, new SystemAssistantMessageRequest("Sprawdź klienta ponownie", "crm", null, null));
            await().until(() -> !service.get(id).busy());
            assertThat(service.get(id).messages()).hasSize(2);
            // Three API operations share one OIDC profile: one preflight per turn.
            verify(oidc, times(2)).authorization(auth);
            service.send(id, new SystemAssistantMessageRequest("Rozmowa bez usuniętego API", "missing", null, null));
            await().until(() -> !service.get(id).busy());
            verifyNoMoreInteractions(oidc);
        } finally { service.close(); }
    }
}
