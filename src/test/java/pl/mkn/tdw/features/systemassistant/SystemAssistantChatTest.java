package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotToolInvocationHandler;
import pl.mkn.tdw.features.systemassistant.ai.*;
import pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationService;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.*;
import pl.mkn.tdw.shared.ai.*;
import pl.mkn.tdw.shared.evidence.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class SystemAssistantChatTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path workspace;
    private SystemAssistantConversationService service(SystemAssistantConfigurationService configurations, SystemAssistantAiPort ai) {
        return new SystemAssistantConversationService(configurations, ai, SystemAssistantTestSupport.history(workspace),
                new pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard(), mock(pl.mkn.tdw.integrations.httpauth.OidcLoginPort.class));
    }

    @Test
    void httpAcceptsOneTurnAndRetainsEvidenceWhenAiFails() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var ai = mock(SystemAssistantAiPort.class);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<AnalysisEvidenceSection> sink = invocation.getArgument(5);
            sink.accept(new AnalysisEvidenceSection("openapi", "call", List.of(new AnalysisEvidenceItem("saved", List.of()))));
            entered.countDown(); release.await(5, TimeUnit.SECONDS);
            throw new IllegalStateException("synthetic failure after tool call");
        });
        var service = service(SystemAssistantTestSupport.configuration((id, yaml) -> List.of()), ai);
        try {
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                    new pl.mkn.tdw.features.systemassistant.api.SystemAssistantConversationController(service))
                    .setControllerAdvice(new pl.mkn.tdw.api.ApiExceptionHandler()).build();
            var base = "/api/system-assistant/conversations";
            var created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base)
                    .contentType("application/json").content("{}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andReturn();
            var id = new ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();
            var messages = base + "/" + id + "/messages";
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(messages)
                    .contentType("application/json").content("{\"message\":\"Demo\",\"profileId\":\"test_profile\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(messages)
                    .contentType("application/json").content("{\"message\":\"Duplicate\",\"profileId\":\"test_profile\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
            release.countDown();
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            var failed = service.get(id).messages().get(1);
            assertThat(failed.status()).isEqualTo("FAILED");
            assertThat(failed.toolEvidenceSections()).hasSize(1);
            assertThat(failed.errorMessage()).doesNotContain("synthetic failure");
        } finally { release.countDown(); service.close(); }
    }

    private SystemAssistantConfiguration config(String instructions) {
        return SystemAssistantTestSupport.config(List.of(), List.of(new SystemAssistantConfiguration.Skill("demo", "Demo", instructions)));
    }
    private SystemAssistantMessageRequest message(String text) { return new SystemAssistantMessageRequest(text, "test_profile", null, null); }

    @Test
    void freezesAcceptedTurnAndUsesCurrentSkillsOnNextMessage() throws Exception {
        var configurations = SystemAssistantTestSupport.configuration((id, yaml) -> List.of());
        configurations.save(config("FIRST"));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var seen = new CopyOnWriteArrayList<String>();
        var historySeen = new AtomicReference<List<AnalysisChatMessageResponse>>();
        var ai = new SystemAssistantAiPort() {
            public void requireReady() {}
            public Reply answer(String turnId, SystemAssistantConfigurationService.Snapshot configuration,
                    SystemAssistantConversation.PageContext page, List<AnalysisChatMessageResponse> history,
                    SystemAssistantMessageRequest request, Consumer<AnalysisEvidenceSection> evidence,
                    Consumer<AnalysisAiActivityEvent> activity) {
                seen.add(configuration.configuration().skills().get(0).content()); historySeen.set(history);
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
                evidence.accept(new AnalysisEvidenceSection("openapi", "fixture", List.of(new AnalysisEvidenceItem("done", List.of()))));
                return new Reply("Odpowiedź " + request.message(), null);
            }
        };
        var service = service(configurations, ai);
        try {
            var id = service.create(null).id();
            var accepted = service.send(id, message("first"));
            assertThat(accepted.busy()).isTrue();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.send(id, message("duplicate"))).isInstanceOf(SystemAssistantException.class);
            assertThatThrownBy(() -> service.remove(id)).isInstanceOf(SystemAssistantException.class);
            configurations.save(config("SECOND")); release.countDown();
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            assertThat(service.get(id).messages().get(1).toolEvidenceSections()).hasSize(1);
            assertThat(service.get(id).messages().get(1).prompt()).isNull();
            service.send(id, message("second"));
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            assertThat(seen).containsExactly("FIRST", "SECOND");
            assertThat(historySeen.get()).hasSize(3);
            service.remove(id);
            assertThatThrownBy(() -> service.get(id)).isInstanceOf(SystemAssistantException.class);
        } finally { release.countDown(); service.close(); }
    }

    @Test
    void createsNewSessionWithoutSdkSkillsOrStaleInstructions() {
        var mapper = new ObjectMapper();
        var sdkTools = new CopilotSdkToolFactory(List.of(), mapper, List.of(), mock(CopilotToolInvocationHandler.class));
        var runner = new SystemAssistantCopilotRunner(() -> "synthetic-fixture", sdkTools,
                new OpenApiToolSetFactory((execution, args) -> null, mapper), null, null, new SystemAssistantPrompt(mapper), pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionCleanup.NO_OP);
        var history = List.of(pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState.completedUser("user", "Pytanie", java.time.Instant.now()).snapshot());
        var run = runner.assemble("one", new SystemAssistantConfigurationService.Snapshot(config("CURRENT"), List.of(), java.util.Map.of()),
                null, history, message("Pytanie"), ignored -> {}, ignored -> {});
        assertThat(run.sessionTarget().existing()).isFalse();
        assertThat(run.sessionConfigRequest().skillsEnabled()).isFalse();
        assertThat(run.sessionConfigRequest().effectiveAvailableToolNames()).isEmpty();
        assertThat(run.sessionConfigRequest().durableSystemInstructions()).contains("CURRENT");
        assertThat(run.prompt()).contains("Pytanie").doesNotContain("CURRENT");
        assertThat(run.initialReport()).isNull();
        assertThat(run.artifactContents()).isEmpty();
    }
}
