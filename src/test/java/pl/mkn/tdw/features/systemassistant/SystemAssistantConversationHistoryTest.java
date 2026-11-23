package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.features.systemassistant.ai.SystemAssistantAiPort;
import pl.mkn.tdw.features.systemassistant.chat.*;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.*;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.localworkspace.storage.*;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState;
import pl.mkn.tdw.shared.evidence.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class SystemAssistantConversationHistoryTest {
    @TempDir Path directory;
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final LocalWorkspaceProperties properties = new LocalWorkspaceProperties();

    LocalAnalysisRunStore store() {
        properties.setDirectory(directory.toString());
        return new FileSystemLocalAnalysisRunStore(properties, new LocalWorkspacePaths(properties), new LocalWorkspaceJsonFileStore(mapper));
    }
    SystemAssistantConversationHistory history(LocalAnalysisRunStore store) { return new SystemAssistantConversationHistory(store, mapper, properties); }
    SystemAssistantMessageRequest request(String text) { return new SystemAssistantMessageRequest(text, "test_profile", null, null); }

    @Test void reopensPersistedTranscriptWithOriginalPageAndCurrentSkillsAndTools() throws Exception {
        var store = store(); var configurations = mock(SystemAssistantConfigurationService.class);
        var old = new SystemAssistantConfigurationService.Snapshot(SystemAssistantTestSupport.config(List.of(),
                List.of(new SystemAssistantConfiguration.Skill("s", "Skill", "OLD_SKILL"))), List.of(), Map.of());
        when(configurations.snapshot()).thenReturn(old);
        var seen = new AtomicReference<SystemAssistantConfigurationService.Snapshot>();
        var seenPage = new AtomicReference<SystemAssistantConversation.PageContext>();
        var transcript = new AtomicReference<List<AnalysisChatMessageResponse>>();
        var ai = mock(SystemAssistantAiPort.class);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            seen.set(call.getArgument(1)); seenPage.set(call.getArgument(2)); transcript.set(call.getArgument(3));
            return new SystemAssistantAiPort.Reply("Odpowiedź", null);
        });
        var page = new SystemAssistantConversation.PageContext("https://crm.example", "/customers/C-100", "Klient", Instant.now().toString());
        String id;
        try (var first = new ServiceClose(new SystemAssistantConversationService(configurations, ai, history(store), new LocalAnalysisRunOperationGuard(), mock(pl.mkn.tdw.integrations.httpauth.OidcLoginPort.class)))) {
            id = first.service.create(page).id(); first.service.send(id, request("Pierwsze pytanie"));
            await().until(() -> !first.service.get(id).busy());
        }
        assertThat(store.listRuns()).singleElement().satisfies(entry -> {
            assertThat(entry.feature()).isEqualTo("system-assistant"); assertThat(entry.name()).isEqualTo("Pierwsze pytanie");
        });
        assertThat(store.findById(id).orElseThrow().exportEnvelope().toString()).doesNotContain("OLD_SKILL");
        var tool = mock(OpenApiOperation.class); when(tool.name()).thenReturn("oa_crm_read");
        var current = new SystemAssistantConfigurationService.Snapshot(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "CRM", "yaml")),
                List.of(new SystemAssistantConfiguration.Skill("s", "Skill", "CURRENT_SKILL"))), List.of(tool), Map.of("oa_crm_read", "crm"));
        when(configurations.snapshot()).thenReturn(current);
        try (var reopened = new ServiceClose(new SystemAssistantConversationService(configurations, ai, history(store()), new LocalAnalysisRunOperationGuard(), mock(pl.mkn.tdw.integrations.httpauth.OidcLoginPort.class)))) {
            assertThat(reopened.service.get(id).messages()).hasSize(2);
            reopened.service.send(id, request("Kontynuacja")); await().until(() -> !reopened.service.get(id).busy());
            assertThat(seen.get()).isEqualTo(current); assertThat(seenPage.get()).isEqualTo(page);
            assertThat(transcript.get()).extracting(AnalysisChatMessageResponse::content).containsExactly("Pierwsze pytanie", "Odpowiedź", "Kontynuacja");
            assertThat(reopened.service.get(id).messages()).hasSize(4);
            store.rename(id, "Moja rozmowa"); reopened.service.send(id, request("Dalej")); await().until(() -> !reopened.service.get(id).busy());
            assertThat(store.listRuns().get(0).name()).isEqualTo("Moja rozmowa");
            store.delete(id); assertThatThrownBy(() -> reopened.service.get(id)).isInstanceOf(SystemAssistantException.class);
            assertThatThrownBy(() -> reopened.service.send(id, request("Nie odtwarzaj"))).isInstanceOf(SystemAssistantException.class);
            assertThat(store.listRuns()).isEmpty();
        }
    }

    @Test void interruptedTurnRetainsEvidenceAndDoesNotReplayAi() {
        var store = store(); var history = history(store); var id = UUID.randomUUID().toString(); var now = Instant.now();
        var assistant = AnalysisChatMessageState.inProgressAssistant("assistant", now);
        assistant.addToolEvidence(new AnalysisEvidenceSection("api", "writes", List.of(new AnalysisEvidenceItem("Zapis wykonany", List.of()))));
        history.save(now, new SystemAssistantConversation(id, true, null,
                List.of(AnalysisChatMessageState.completedUser("user", "Zapisz", now).snapshot(), assistant.snapshot())));
        var ai = mock(SystemAssistantAiPort.class);
        try (var service = new ServiceClose(new SystemAssistantConversationService(mock(SystemAssistantConfigurationService.class), ai, history, new LocalAnalysisRunOperationGuard(), mock(pl.mkn.tdw.integrations.httpauth.OidcLoginPort.class)))) {
            service.service.recoverInterruptedTurns();
            var restored = service.service.get(id);
            assertThat(restored.busy()).isFalse(); assertThat(restored.messages().get(restored.messages().size() - 1).errorCode()).isEqualTo("TURN_INTERRUPTED");
            assertThat(restored.messages().get(restored.messages().size() - 1).toolEvidenceSections()).hasSize(1);
            assertThat(store.listRuns().get(0).status()).isEqualTo("FAILED"); verifyNoInteractions(ai);
        }
    }

    @Test void aFailedFinalWriteCanBeRecoveredWithoutReplayingTheAnswer() throws Exception {
        var store = spy(store()); var history = history(store);
        var ai = mock(SystemAssistantAiPort.class); var finished = new CountDownLatch(1);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any())).thenReturn(new SystemAssistantAiPort.Reply("Wykonano", null));
        try (var service = new ServiceClose(new SystemAssistantConversationService(SystemAssistantTestSupport.configuration((id, yaml) -> List.of()), ai,
                history, new LocalAnalysisRunOperationGuard(), mock(pl.mkn.tdw.integrations.httpauth.OidcLoginPort.class)))) {
            var id = service.service.create(null).id();
            doAnswer(call -> {
                LocalAnalysisRunRecord record = call.getArgument(1);
                if (!record.exportEnvelope().path("conversation").path("busy").asBoolean()
                        && record.exportEnvelope().path("conversation").path("messages").size() > 0) {
                    finished.countDown(); throw new IllegalStateException("storage offline");
                }
                return call.callRealMethod();
            }).when(store).save(any(), any());
            service.service.send(id, request("Jedna operacja")); assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.service.get(id)).isInstanceOf(SystemAssistantException.class);
            doCallRealMethod().when(store).save(any(), any());
            assertThat(service.service.get(id).messages().get(1).content()).isEqualTo("Wykonano");
            verify(ai, times(1)).answer(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test void failedAcceptanceWriteDoesNotExecuteAiAndActiveTurnBlocksHistoryDeletion() throws Exception {
        var store = spy(store()); var history = history(store); var guard = new LocalAnalysisRunOperationGuard();
        var configurations = SystemAssistantTestSupport.configuration((id, yaml) -> List.of());
        var ai = mock(SystemAssistantAiPort.class); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            entered.countDown(); release.await(5, TimeUnit.SECONDS); return new SystemAssistantAiPort.Reply("Gotowe", null);
        });
        try (var service = new ServiceClose(new SystemAssistantConversationService(configurations, ai, history, guard, mock(pl.mkn.tdw.integrations.httpauth.OidcLoginPort.class)))) {
            var id = service.service.create(null).id();
            doThrow(new IllegalStateException("storage offline")).when(store).save(any(), any());
            assertThatThrownBy(() -> service.service.send(id, request("Nie wykonuj"))).isInstanceOf(SystemAssistantException.class);
            verify(ai, never()).answer(any(), any(), any(), any(), any(), any(), any());
            doCallRealMethod().when(store).save(any(), any());
            service.service.send(id, request("Wykonaj")); assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(guard.tryAcquire(id)).isEmpty(); assertThatThrownBy(() -> service.service.remove(id)).isInstanceOf(SystemAssistantException.class);
            release.countDown(); await().until(() -> !service.service.get(id).busy());
            service.service.remove(id); assertThat(store.listRuns()).isEmpty();
        } finally { release.countDown(); }
    }

    private record ServiceClose(SystemAssistantConversationService service) implements AutoCloseable {
        public void close() { service.close(); }
    }
}
