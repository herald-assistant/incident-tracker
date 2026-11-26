package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotToolInvocationHandler;
import pl.mkn.tdw.features.systemassistant.ai.*;
import pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationService;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantProfileResources;
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
        when(ai.answer(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
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
    private SystemAssistantMessageRequest message(String text) { return new SystemAssistantMessageRequest(text, "test_profile", null, null, null); }

    @Test
    void freezesTheFullPageUrlPerTurnAndPreservesTheOpeningPageInHistory() {
        var seen = new CopyOnWriteArrayList<String>();
        var prompts = new SystemAssistantPrompt(new ObjectMapper());
        var ai = mock(SystemAssistantAiPort.class);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            SystemAssistantMessageRequest turn = call.getArgument(4);
            seen.add(prompts.conversation(call.getArgument(2), turn.pageContext(), call.getArgument(3)));
            return new SystemAssistantAiPort.Reply("OK", null);
        });
        var a = new SystemAssistantMessageRequest.CurrentPage("https://crm.example", "/cases/1",
                "https://crm.example/cases/1?tab=one#row", "Ekran A", "2026-11-26T10:00:00Z");
        var b = new SystemAssistantMessageRequest.CurrentPage(a.origin(), "/cases/2",
                "https://crm.example/cases/2?id=a%2Fb&id=c#details", "Ekran B", a.capturedAt());
        var service = service(SystemAssistantTestSupport.configuration((id, yaml) -> List.of()), ai);
        try {
            var id = service.create(a.openingContext()).id();
            for (var page : List.of(a, b)) {
                service.send(id, new SystemAssistantMessageRequest("Opisz ekran", "test_profile", null, null, page));
                await().until(() -> !service.get(id).busy());
            }
            assertThat(seen).hasSize(2);
            assertThat(seen.get(0)).contains(a.url()).doesNotContain(b.url());
            assertThat(seen.get(1)).contains(b.url(), b.title()).doesNotContain(a.url());
            var invalid = new SystemAssistantMessageRequest.CurrentPage(a.origin(), a.pathname(), b.url(), a.title(), a.capturedAt());
            assertThatThrownBy(() -> service.send(id, new SystemAssistantMessageRequest("Dalej", "test_profile", null, null, invalid)))
                    .isInstanceOf(SystemAssistantException.class);
            assertThat(service.get(id).messages()).hasSize(4);
            assertThat(service.get(id).pageContext()).isEqualTo(a.openingContext());
        } finally { service.close(); }
    }

    @Test
    void acceptsLongMessagesAndPassesFullHistoryWithToolResultsToAi() throws Exception {
        var mapper = new ObjectMapper();
        var prompts = new SystemAssistantPrompt(mapper);
        var longMessage = "Pytanie " + "ą".repeat(13_000) + " KONIEC_WIADOMOSCI";
        var longResult = "Wynik " + "ż".repeat(120_000) + " KONIEC_WYNIKU";
        var seenPrompts = new CopyOnWriteArrayList<String>();
        var ai = mock(SystemAssistantAiPort.class);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            seenPrompts.add(prompts.conversation(invocation.getArgument(2), ((SystemAssistantMessageRequest) invocation.getArgument(4)).pageContext(), invocation.getArgument(3)));
            Consumer<AnalysisEvidenceSection> sink = invocation.getArgument(5);
            sink.accept(new AnalysisEvidenceSection("openapi", "call", List.of(new AnalysisEvidenceItem("result",
                    List.of(new AnalysisEvidenceAttribute("result", longResult))))));
            return new SystemAssistantAiPort.Reply("Odpowiedź", null);
        });
        var service = service(SystemAssistantTestSupport.configuration((id, yaml) -> List.of()), ai);
        try (var validator = new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                    new pl.mkn.tdw.features.systemassistant.api.SystemAssistantConversationController(service))
                    .setControllerAdvice(new pl.mkn.tdw.api.ApiExceptionHandler()).setValidator(validator).build();
            var id = service.create(null).id();
            var endpoint = "/api/system-assistant/conversations/" + id + "/messages";
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                    .contentType("application/json").content(mapper.writeValueAsString(message("   "))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                    .contentType("application/json").content(mapper.writeValueAsString(message(longMessage))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                    .contentType("application/json").content(mapper.writeValueAsString(message("Kontynuuj"))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            assertThat(seenPrompts).hasSize(2);
            assertThat(seenPrompts.get(1)).hasSizeGreaterThan(120_000).contains(longMessage, longResult, "Kontynuuj");
            assertThat(service.get(id).messages()).hasSize(4)
                    .allSatisfy(message -> assertThat(message.status()).isEqualTo("COMPLETED"));
        } finally { service.close(); }
    }

    @Test
    void continuesPastTwentyTurnsAfterRestoringConversation() {
        var configurations = SystemAssistantTestSupport.configuration((id, yaml) -> List.of());
        var ai = mock(SystemAssistantAiPort.class);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SystemAssistantAiPort.Reply("Odpowiedź", null));
        var service = service(configurations, ai);
        String id;
        try {
            id = service.create(null).id();
            for (var turn = 1; turn <= 20; turn++) {
                service.send(id, message("Tura " + turn));
                await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            }
        } finally { service.close(); }
        var restored = service(configurations, ai);
        try {
            assertThat(restored.get(id).messages()).hasSize(40);
            restored.send(id, message("Tura 21"));
            await().atMost(5, TimeUnit.SECONDS).until(() -> !restored.get(id).busy());
            assertThat(restored.get(id).messages()).hasSize(42)
                    .allSatisfy(message -> assertThat(message.status()).isEqualTo("COMPLETED"));
            assertThat(SystemAssistantTestSupport.history(workspace).load(id).conversation().messages()).hasSize(42);
            verify(ai, times(21)).answer(any(), any(), any(), any(), any(), any(), any(), any());
        } finally { restored.close(); }
    }

    @Test
    void freezesAcceptedTurnAndUsesCurrentSkillsOnNextMessage() throws Exception {
        var configurations = SystemAssistantTestSupport.configuration((id, yaml) -> List.of());
        configurations.save(config("FIRST"));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var seen = new CopyOnWriteArrayList<String>();
        var historySeen = new AtomicReference<List<AnalysisChatMessageResponse>>();
        var ai = new SystemAssistantAiPort() {
            public void requireReady() {}
            public Reply answer(String turnId, SystemAssistantProfileResources configuration,
                    SystemAssistantConversation.PageContext page, List<AnalysisChatMessageResponse> history,
                    SystemAssistantMessageRequest request, Consumer<AnalysisEvidenceSection> evidence,
                    Consumer<AnalysisAiActivityEvent> activity, List<SystemAssistantAiPort.Attachment> attachments) {
                seen.add(configuration.skills().get(0).content()); historySeen.set(history);
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
        var run = runner.assemble("one", new SystemAssistantProfileResources(config("CURRENT").skills(), List.of()),
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
