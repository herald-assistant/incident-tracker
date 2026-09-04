package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import com.github.copilot.ConnectionState;
import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.generated.SessionEvent;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.generated.AssistantMessageToolRequest;
import com.github.copilot.generated.AssistantReasoningEvent;
import com.github.copilot.generated.AssistantUsageEvent;
import com.github.copilot.generated.SessionCompactionStartEvent;
import com.github.copilot.generated.SessionUsageInfoEvent;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.ResumeSessionConfig;
import com.github.copilot.rpc.SessionConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotClientShutdown;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeCompatibility;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeVersionInfo;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierPolicy;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierPreference;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotEffectiveContextTier;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotEffectiveContextTierReader;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsResponse;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetProperties;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetRegistry;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportSessionStore;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.executionGateway;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotSdkExecutionGatewayTest {

    @Test
    void shouldUseDefaultSendAndWaitTimeoutFromProperties() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var preparedRequest = new CopilotPreparedSession(
                "corr-123",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                Map.of()
        );
        var sessionRef = new AtomicReference<CopilotSession>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);
            sessionRef.set(session);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("session-123");
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenReturn(CompletableFuture.completedFuture(assistantMessage("Structured answer")));
        })) {
            var response = gateway.execute(preparedRequest);

            assertEquals("Structured answer", response.content());
            assertEquals("session-123", response.sessionId());
            verify(sessionRef.get()).sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L));
        }
    }

    @Test
    void shouldUseConfiguredSendAndWaitTimeout() {
        var properties = new CopilotSdkProperties();
        properties.setSendAndWaitTimeout(Duration.ofSeconds(90));
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var preparedRequest = new CopilotPreparedSession(
                "corr-456",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                Map.of()
        );
        var sessionRef = new AtomicReference<CopilotSession>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);
            sessionRef.set(session);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("session-456");
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(90_000L)))
                    .thenReturn(CompletableFuture.completedFuture(assistantMessage("Configured timeout answer")));
        })) {
            var response = gateway.execute(preparedRequest);

            assertEquals("Configured timeout answer", response.content());
            verify(sessionRef.get()).sendAndWait(same(preparedRequest.messageOptions()), eq(90_000L));
        }
    }

    @Test
    void shouldExecuteWithArtifactOnlyPreparedRequest() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );

        var preparedRequest = new CopilotPreparedSession(
                "corr-cleanup",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                Map.of("00-incident-manifest.json", "{}")
        );

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("session-cleanup");
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenReturn(CompletableFuture.completedFuture(assistantMessage("Structured answer")));
        })) {
            assertEquals("Structured answer", gateway.execute(preparedRequest).content());
        }
    }

    @Test
    void shouldResumeExistingCopilotSessionWhenSessionTargetIsExisting() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var resumeSessionConfig = new ResumeSessionConfig();
        var preparedRequest = new CopilotPreparedSession(
                "corr-follow-up",
                CopilotSessionTarget.existing("session-resume-1"),
                new CopilotClientOptions(),
                new SessionConfig(),
                resumeSessionConfig,
                new MessageOptions().setPrompt("Czyli co dalej?"),
                "Czyli co dalej?",
                Map.of(),
                section -> {
                },
                event -> {
                }
        );
        var sessionRef = new AtomicReference<CopilotSession>();

        try (MockedConstruction<CopilotClient> mockedClients = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);
            sessionRef.set(session);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.resumeSession(eq("session-resume-1"), same(resumeSessionConfig)))
                    .thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("session-resume-1");
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenReturn(CompletableFuture.completedFuture(assistantMessage("Follow-up answer")));
        })) {
            var response = gateway.execute(preparedRequest);

            assertEquals("Follow-up answer", response.content());
            assertEquals("session-resume-1", response.sessionId());
            verify(sessionRef.get()).sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L));
            verify(mockedClients.constructed().get(0), never()).createSession(any(SessionConfig.class));
        }
    }

    @Test
    void shouldReturnTokenUsageEventsInExecutionResult() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var sessionId = "analysis-usage";
        var preparedRequest = new CopilotPreparedSession(
                "corr-usage",
                new CopilotClientOptions(),
                new SessionConfig().setSessionId(sessionId),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                Map.of()
        );
        var eventHandler = new AtomicReference<Consumer<SessionEvent>>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn(sessionId);
            when(session.on(isA(Consumer.class))).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                var handler = (Consumer<SessionEvent>) invocation.getArgument(0);
                eventHandler.set(handler);
                return (Closeable) () -> {
                };
            });
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenAnswer(invocation -> {
                        eventHandler.get().accept(assistantUsage("gpt-5.4", 2400D, 420D, 300D, 50D, 2.3D, 1100D));
                        eventHandler.get().accept(sessionUsageInfo(128000D, 9200D, 6D));
                        return CompletableFuture.completedFuture(assistantMessage("Structured answer"));
                    });
        })) {
            var response = gateway.execute(preparedRequest);

            assertEquals("Structured answer", response.content());
            var usage = response.usage();
            assertNotNull(usage);
            assertEquals(2400L, usage.inputTokens());
            assertEquals(420L, usage.outputTokens());
            assertEquals(2820L, usage.totalTokens());
            assertEquals(300L, usage.cacheReadTokens());
            assertEquals(50L, usage.cacheWriteTokens());
            assertEquals(1, usage.apiCallCount());
            assertEquals("gpt-5.4", usage.model());
            assertEquals(128000L, usage.contextTokenLimit());
            assertEquals(9200L, usage.contextCurrentTokens());
            assertEquals(6L, usage.contextMessages());
        }
    }

    @Test
    void shouldVerifyRequiredLongContextBeforeSendingFirstCrmMessage() {
        var properties = new CopilotSdkProperties();
        properties.getContextTier().setEstimatedCharactersPerToken(4D);
        properties.getContextTier().setReservedTokens(0);
        var effectiveTierReader = mock(CopilotEffectiveContextTierReader.class);
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper()),
                new CopilotReportSessionStore(),
                effectiveTierReader
        );
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var sessionConfig = new SessionConfig()
                .setSessionId("crm-context-session")
                .setModel("gpt-synthetic-crm")
                .setReasoningEffort("medium");
        var preparedRequest = new CopilotPreparedSession(
                "crm-context-run",
                CopilotSessionTarget.newSession(),
                new CopilotClientOptions(),
                sessionConfig,
                new ResumeSessionConfig().setModel("gpt-synthetic-crm"),
                new MessageOptions().setPrompt("Review the synthetic CRM contact screen."),
                "Review the synthetic CRM contact screen.",
                Map.of(),
                null,
                evidence -> {
                },
                activities::add,
                CopilotRunAuth.localToken(),
                CopilotContextTierPreference.LONG_CONTEXT_REQUIRED
        );
        var eventHandler = new AtomicReference<Consumer<SessionEvent>>();
        var sessionRef = new AtomicReference<CopilotSession>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);
            sessionRef.set(session);
            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(same(sessionConfig))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("crm-context-session");
            when(session.on(any())).thenAnswer(invocation -> {
                eventHandler.set(invocation.getArgument(0));
                return (Closeable) () -> {
                };
            });
            when(effectiveTierReader.read(session)).thenReturn(new CopilotEffectiveContextTier(
                    "gpt-synthetic-crm",
                    "medium",
                    "long_context"
            ));
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenAnswer(invocation -> {
                        eventHandler.get().accept(sessionUsageInfo(272_000, 127_863, 4));
                        return CompletableFuture.completedFuture(assistantMessage("Synthetic CRM answer"));
                    });
        })) {
            var response = gateway.execute(preparedRequest);

            assertEquals("Synthetic CRM answer", response.content());
            assertThat(sessionConfig.getContextTier()).isEqualTo("long_context");
            var ordering = org.mockito.Mockito.inOrder(effectiveTierReader, sessionRef.get());
            ordering.verify(effectiveTierReader).read(sessionRef.get());
            ordering.verify(sessionRef.get()).sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L));
            assertThat(activities).filteredOn(activity -> "platform.context_tier".equals(activity.type()))
                    .extracting(AnalysisAiActivityEvent::status)
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED");
            assertThat(activities).filteredOn(activity -> "platform.context_tier".equals(activity.type()))
                    .element(2)
                    .satisfies(activity -> assertThat(activity.details())
                            .containsEntry("phase", "EFFECTIVE_WINDOW_OBSERVED")
                            .containsEntry("tokenLimit", 272_000L)
                            .containsEntry("currentTokens", 127_863L));
        }
    }

    @Test
    void shouldAbortAndResumeSameCrmSessionWithLongContextAfterRuntimeThreshold() {
        var properties = new CopilotSdkProperties();
        properties.getContextTier().setInitialPromptThreshold(0.70D);
        properties.getContextTier().setRuntimeUsageThreshold(0.70D);
        properties.getContextTier().setEstimatedCharactersPerToken(4D);
        properties.getContextTier().setReservedTokens(0);
        var evidenceStore = mock(CopilotToolEvidenceSessionStore.class);
        var budgetRegistry = mock(CopilotToolBudgetRegistry.class);
        when(budgetRegistry.unregisterSession("crm-runtime-session")).thenReturn(Optional.empty());
        var effectiveTierReader = mock(CopilotEffectiveContextTierReader.class);
        var gateway = new CopilotSdkExecutionGateway(
                properties,
                evidenceStore,
                budgetRegistry,
                new CopilotReportSessionStore(),
                new CopilotClientShutdown(properties),
                contextTierPolicy(properties, effectiveTierReader),
                compatibleRuntime()
        );
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var sessionConfig = new SessionConfig()
                .setSessionId("crm-runtime-session")
                .setModel("gpt-synthetic-crm");
        var resumeConfig = new ResumeSessionConfig().setModel("gpt-synthetic-crm");
        var initialMessage = new MessageOptions().setPrompt("Review a synthetic CRM contact card.");
        var preparedRequest = new CopilotPreparedSession(
                "crm-runtime-tier-run",
                CopilotSessionTarget.newSession(),
                new CopilotClientOptions(),
                sessionConfig,
                resumeConfig,
                initialMessage,
                initialMessage.getPrompt(),
                Map.of(),
                null,
                evidence -> {
                },
                activities::add,
                CopilotRunAuth.localToken(),
                CopilotContextTierPreference.AUTO
        );
        var firstHandler = new AtomicReference<Consumer<SessionEvent>>();
        var resumedHandler = new AtomicReference<Consumer<SessionEvent>>();
        var firstSession = mock(CopilotSession.class);
        var resumedSession = mock(CopilotSession.class);
        when(firstSession.getSessionId()).thenReturn("crm-runtime-session");
        when(resumedSession.getSessionId()).thenReturn("crm-runtime-session");
        when(firstSession.abort()).thenReturn(CompletableFuture.completedFuture(null));
        when(firstSession.on(isA(Consumer.class))).thenAnswer(invocation -> {
            firstHandler.set(invocation.getArgument(0));
            return (Closeable) () -> {
            };
        });
        when(resumedSession.on(isA(Consumer.class))).thenAnswer(invocation -> {
            resumedHandler.set(invocation.getArgument(0));
            return (Closeable) () -> {
            };
        });
        when(firstSession.sendAndWait(same(initialMessage), eq(300_000L))).thenAnswer(invocation -> {
            firstHandler.get().accept(sessionUsageInfo(100, 70, 8));
            return CompletableFuture.completedFuture(assistantMessage("Partial CRM answer ignored after abort"));
        });
        when(resumedSession.sendAndWait(any(MessageOptions.class), eq(300_000L))).thenAnswer(invocation -> {
            resumedHandler.get().accept(assistantUsage(
                    "gpt-synthetic-crm", 2_400D, 420D, 300D, 50D, 2.3D, 1_100D
            ));
            resumedHandler.get().accept(sessionUsageInfo(1_000, 92, 9));
            return CompletableFuture.completedFuture(assistantMessage("Complete synthetic CRM report"));
        });
        when(effectiveTierReader.read(resumedSession)).thenReturn(new CopilotEffectiveContextTier(
                "gpt-synthetic-crm",
                "medium",
                null
        ));

        try (MockedConstruction<CopilotClient> mockedClients = mockConstruction(
                CopilotClient.class,
                (client, context) -> {
                    when(client.getState()).thenReturn(ConnectionState.CONNECTED);
                    when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
                    when(client.createSession(same(sessionConfig)))
                            .thenReturn(CompletableFuture.completedFuture(firstSession));
                    when(client.resumeSession("crm-runtime-session", resumeConfig))
                            .thenReturn(CompletableFuture.completedFuture(resumedSession));
                    when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
                }
        )) {
            var result = gateway.execute(preparedRequest);

            assertThat(result.content()).isEqualTo("Complete synthetic CRM report");
            assertThat(result.sessionId()).isEqualTo("crm-runtime-session");
            assertThat(result.usage().contextTokenLimit()).isEqualTo(1_000L);
            assertThat(resumeConfig.getContextTier()).isEqualTo("long_context");
            var continuation = org.mockito.ArgumentCaptor.forClass(MessageOptions.class);
            verify(resumedSession).sendAndWait(continuation.capture(), eq(300_000L));
            assertThat(continuation.getValue().getPrompt())
                    .contains("Kontynuuj przerwany turn")
                    .doesNotContain(initialMessage.getPrompt());
            verify(firstSession).abort();
            verify(evidenceStore).registerSession(eq("crm-runtime-session"), any());
            verify(evidenceStore).unregisterSession("crm-runtime-session");
            verify(budgetRegistry).registerSession("crm-runtime-session");
            verify(budgetRegistry).unregisterSession("crm-runtime-session");
            verify(mockedClients.constructed().get(0))
                    .resumeSession("crm-runtime-session", resumeConfig);
            assertThat(activities).filteredOn(activity -> "platform.context_tier".equals(activity.type()))
                    .extracting(activity -> activity.details().get("phase"))
                    .containsExactly(
                            "RUNTIME_TIER_SWITCH_REQUESTED",
                            "RUNTIME_SESSION_ABORTED",
                            "RUNTIME_RESUME_REQUESTED",
                            "MODEL_STATE_VERIFICATION",
                            "EFFECTIVE_WINDOW_OBSERVED"
                    );
            assertThat(activities).filteredOn(activity -> "platform.context_tier".equals(activity.type()))
                    .element(3)
                    .satisfies(activity -> {
                        assertThat(activity.status()).isEqualTo("WARNING");
                        assertThat(activity.details()).containsEntry("verification", "TIER_UNCONFIRMED");
                    });
            assertThat(activities).filteredOn(activity -> "platform.context_tier".equals(activity.type()))
                    .element(4)
                    .satisfies(activity -> assertThat(activity.details())
                            .containsEntry("verification", "TOKEN_LIMIT_INCREASED")
                            .containsEntry("runtimeUpgradeConfirmed", true));
        }
    }

    @Test
    void shouldNotSendCrmPromptWhenRequiredLongContextIsNotEffective() {
        var properties = new CopilotSdkProperties();
        var effectiveTierReader = mock(CopilotEffectiveContextTierReader.class);
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper()),
                new CopilotReportSessionStore(),
                effectiveTierReader
        );
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var sessionConfig = new SessionConfig()
                .setSessionId("crm-context-rejected-session")
                .setModel("gpt-synthetic-crm");
        var preparedRequest = new CopilotPreparedSession(
                "crm-context-rejected-run",
                CopilotSessionTarget.newSession(),
                new CopilotClientOptions(),
                sessionConfig,
                new ResumeSessionConfig().setModel("gpt-synthetic-crm"),
                new MessageOptions().setPrompt("Review the synthetic CRM contact screen."),
                "Review the synthetic CRM contact screen.",
                Map.of(),
                null,
                evidence -> {
                },
                activities::add,
                CopilotRunAuth.localToken(),
                CopilotContextTierPreference.LONG_CONTEXT_REQUIRED
        );
        var sessionRef = new AtomicReference<CopilotSession>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);
            sessionRef.set(session);
            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(same(sessionConfig))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(effectiveTierReader.read(session)).thenReturn(new CopilotEffectiveContextTier(
                    "gpt-synthetic-crm",
                    "medium",
                    "default"
            ));
        })) {
            assertThatThrownBy(() -> gateway.execute(preparedRequest))
                    .isInstanceOf(CopilotSdkInvocationException.class)
                    .hasMessageContaining("did not activate long_context");

            verify(sessionRef.get(), never()).sendAndWait(any(MessageOptions.class), eq(300_000L));
            assertThat(sessionConfig.getContextTier()).isEqualTo("long_context");
            assertThat(activities).filteredOn(activity -> "platform.context_tier".equals(activity.type()))
                    .extracting(AnalysisAiActivityEvent::status)
                    .containsExactly("COMPLETED", "FAILED");
        }
    }

    @Test
    void shouldPublishReasoningTextInActivityEvents() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var sessionId = "analysis-reasoning";
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var preparedRequest = new CopilotPreparedSession(
                "corr-reasoning",
                new CopilotClientOptions(),
                new SessionConfig().setSessionId(sessionId),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                Map.of()
        ).withActivitySink(activities::add);
        var eventHandler = new AtomicReference<Consumer<SessionEvent>>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn(sessionId);
            when(session.on(isA(Consumer.class))).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                var handler = (Consumer<SessionEvent>) invocation.getArgument(0);
                eventHandler.set(handler);
                return (Closeable) () -> {
                };
            });
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenAnswer(invocation -> {
                        var reasoningText = "Analizuję stack trace przed wywołaniem toola. Potem sprawdzę repozytorium.";
                        eventHandler.get().accept(assistantReasoning("reasoning-1", reasoningText));
                        eventHandler.get().accept(assistantMessageWithReasoning(
                                "",
                                reasoningText,
                                List.of(new AssistantMessageToolRequest(
                                        "tool-call-1",
                                        "gitlab_find_class_references",
                                        Map.of("reason", "Sprawdzam klasę z stack trace."),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ))
                        ));
                        return CompletableFuture.completedFuture(assistantMessage("Structured answer"));
                    });
        })) {
            var response = gateway.execute(preparedRequest);

            assertEquals("Structured answer", response.content());
            var reasoningActivity = activities.stream()
                    .filter(activity -> "assistant.reasoning".equals(activity.type()))
                    .findFirst()
                    .orElseThrow();
            var messageActivity = activities.stream()
                    .filter(activity -> "assistant.message".equals(activity.type()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("Rozumowanie AI", reasoningActivity.title());
            assertEquals("Analizuję stack trace przed wywołaniem toola.", reasoningActivity.summary());
            assertEquals(
                    "Analizuję stack trace przed wywołaniem toola. Potem sprawdzę repozytorium.",
                    reasoningActivity.details().get("contentPreview")
            );
            assertEquals("Analizuję stack trace przed wywołaniem toola.", messageActivity.summary());
            assertEquals(
                    "Analizuję stack trace przed wywołaniem toola. Potem sprawdzę repozytorium.",
                    messageActivity.details().get("reasoningTextPreview")
            );
            assertEquals(1, messageActivity.details().get("toolRequestCount"));
        }
    }

    @Test
    void shouldPublishCompactionStartActivityEvent() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var preparedRequest = new CopilotPreparedSession(
                "corr-compaction",
                new CopilotClientOptions(),
                new SessionConfig().setSessionId("analysis-compaction"),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                Map.of()
        ).withActivitySink(activities::add);
        var eventHandler = new AtomicReference<Consumer<SessionEvent>>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("analysis-compaction");
            when(session.on(isA(Consumer.class))).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                var handler = (Consumer<SessionEvent>) invocation.getArgument(0);
                eventHandler.set(handler);
                return (Closeable) () -> {
                };
            });
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenAnswer(invocation -> {
                        eventHandler.get().accept(compactionStart(1200L, 56000L, 8000L));
                        return CompletableFuture.completedFuture(assistantMessage("Structured answer"));
                    });
        })) {
            var response = gateway.execute(preparedRequest);

            assertEquals("Structured answer", response.content());
            var compactionActivity = activities.stream()
                    .filter(activity -> "session.compaction_start".equals(activity.type()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("CONTEXT", compactionActivity.category());
            assertEquals("STARTED", compactionActivity.status());
            assertEquals("Kompakcja kontekstu", compactionActivity.title());
            assertEquals("Copilot rozpoczął kompaktowanie kontekstu sesji.", compactionActivity.summary());
            assertEquals(1200L, compactionActivity.details().get("systemTokens"));
            assertEquals(56000L, compactionActivity.details().get("conversationTokens"));
            assertEquals(8000L, compactionActivity.details().get("toolDefinitionsTokens"));
        }
    }

    @Test
    void shouldReturnLatestReportSnapshotAndUnregisterReportAfterExecution() {
        var properties = new CopilotSdkProperties();
        var reportStore = new CopilotReportSessionStore();
        var gateway = executionGateway(properties, new CopilotToolEvidenceSessionStore(), reportStore);
        var preparedRequest = new CopilotPreparedSession(
                "report-run",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Generate report"),
                "Generate report",
                Map.of()
        ).withInitialReport(report("report-1"));

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("session-report");
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenAnswer(invocation -> {
                        assertTrue(reportStore.current("report-1").isPresent());
                        reportStore.upsertSection(
                                "report-1",
                                new AnalysisReportSection(
                                        "OVERVIEW",
                                        "Overview",
                                        1,
                                        "Report updated by tool.",
                                        AnalysisReportMeta.empty()
                                )
                        );
                        return CompletableFuture.completedFuture(assistantMessage("Report saved."));
                    });
        })) {
            var response = gateway.execute(preparedRequest);

            assertEquals("Report saved.", response.content());
            assertNotNull(response.report());
            assertEquals("report-1", response.report().reportId());
            assertEquals("Report updated by tool.", response.report().sections().get(0).markdown());
            assertFalse(reportStore.current("report-1").isPresent());
        }
    }

    @Test
    void shouldUnregisterReportWhenSendAndWaitFails() {
        var properties = new CopilotSdkProperties();
        var reportStore = new CopilotReportSessionStore();
        var gateway = executionGateway(properties, new CopilotToolEvidenceSessionStore(), reportStore);
        var preparedRequest = new CopilotPreparedSession(
                "report-failure-run",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Generate report"),
                "Generate report",
                Map.of()
        ).withInitialReport(report("report-1"));

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("session-report-failure");
            when(session.sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L)))
                    .thenAnswer(invocation -> {
                        assertTrue(reportStore.current("report-1").isPresent());
                        return CompletableFuture.failedFuture(new RuntimeException("model failed"));
                    });
        })) {
            assertThrows(CopilotSdkInvocationException.class, () -> gateway.execute(preparedRequest));
            assertFalse(reportStore.current("report-1").isPresent());
        }
    }

    @Test
    void shouldNotClosePreparedRequestAfterExecution() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var messageOptions = new MessageOptions().setPrompt("Diagnose incident");
        var preparedRequest = mock(CopilotPreparedSession.class);

        when(preparedRequest.runReference()).thenReturn("corr-gateway-owned");
        when(preparedRequest.clientOptions()).thenReturn(new CopilotClientOptions());
        when(preparedRequest.sessionConfig()).thenReturn(new SessionConfig());
        when(preparedRequest.messageOptions()).thenReturn(messageOptions);

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn("session-gateway-owned");
            when(session.sendAndWait(same(messageOptions), eq(300_000L)))
                    .thenReturn(CompletableFuture.completedFuture(assistantMessage("Structured answer")));
        })) {
            assertEquals("Structured answer", gateway.execute(preparedRequest).content());
        }

        verify(preparedRequest, never()).close();
    }

    @Test
    void shouldStopClientWhenClientStartFails() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var preparedRequest = new CopilotPreparedSession(
                "start-failure",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                Map.of()
        );

        try (MockedConstruction<CopilotClient> mockedClients = mockConstruction(CopilotClient.class, (client, context) -> {
            when(client.getState()).thenReturn(ConnectionState.ERROR);
            when(client.start()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("start failed")));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
        })) {
            assertThrows(CopilotSdkInvocationException.class, () -> gateway.execute(preparedRequest));

            verify(mockedClients.constructed().get(0)).stop();
        }
    }

    @Test
    void shouldRejectIncompatibleCliBeforeOpeningSessionAndPublishVersions() {
        var properties = new CopilotSdkProperties();
        var compatibility = mock(CopilotRuntimeCompatibility.class);
        when(compatibility.inspect(any())).thenReturn(new CopilotRuntimeVersionInfo(
                "1.0.11",
                "1.0.56-9",
                3,
                "1.0.57",
                false
        ));
        var gateway = new CopilotSdkExecutionGateway(
                properties,
                toolEvidenceSessionStore(new com.fasterxml.jackson.databind.ObjectMapper()),
                new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties()),
                new CopilotReportSessionStore(),
                new CopilotClientShutdown(properties),
                contextTierPolicy(properties, mock(CopilotEffectiveContextTierReader.class)),
                compatibility
        );
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var preparedRequest = new CopilotPreparedSession(
                "incompatible-runtime",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                Map.of()
        ).withActivitySink(activities::add);

        try (MockedConstruction<CopilotClient> mockedClients = mockConstruction(CopilotClient.class, (client, context) -> {
            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
        })) {
            assertThatThrownBy(() -> gateway.execute(preparedRequest))
                    .isInstanceOf(CopilotSdkInvocationException.class)
                    .hasMessageContaining("required CLI version is 1.0.57 or newer");

            var client = mockedClients.constructed().get(0);
            verify(client, never()).createSession(any());
            verify(client).stop();
        }

        assertThat(activities).singleElement().satisfies(activity -> {
            assertThat(activity.type()).isEqualTo("platform.copilot_runtime");
            assertThat(activity.status()).isEqualTo("FAILED");
            assertThat(activity.details())
                    .containsEntry("sdkVersion", "1.0.11")
                    .containsEntry("cliVersion", "1.0.56-9")
                    .containsEntry("compatible", false);
        });
    }

    private CopilotSdkExecutionGateway executionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore
    ) {
        return executionGateway(properties, toolEvidenceSessionStore, new CopilotReportSessionStore());
    }

    private CopilotSdkExecutionGateway executionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotReportSessionStore reportStore
    ) {
        return executionGateway(
                properties,
                toolEvidenceSessionStore,
                reportStore,
                mock(CopilotEffectiveContextTierReader.class)
        );
    }

    private CopilotSdkExecutionGateway executionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotReportSessionStore reportStore,
            CopilotEffectiveContextTierReader effectiveTierReader
    ) {
        return new CopilotSdkExecutionGateway(
                properties,
                toolEvidenceSessionStore,
                new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties()),
                reportStore,
                new CopilotClientShutdown(properties),
                contextTierPolicy(properties, effectiveTierReader),
                compatibleRuntime()
        );
    }

    private CopilotRuntimeCompatibility compatibleRuntime() {
        var compatibility = mock(CopilotRuntimeCompatibility.class);
        when(compatibility.inspect(any())).thenReturn(new CopilotRuntimeVersionInfo(
                "1.0.11",
                "1.0.57-5",
                3,
                "1.0.57",
                true
        ));
        return compatibility;
    }

    private CopilotContextTierPolicy contextTierPolicy(
            CopilotSdkProperties properties,
            CopilotEffectiveContextTierReader effectiveTierReader
    ) {
        return new CopilotContextTierPolicy(
                properties,
                auth -> new CopilotModelOptionsResponse(
                        "gpt-synthetic-crm",
                        "medium",
                        List.of("low", "medium", "high"),
                        List.of(new CopilotModelOption(
                                "gpt-synthetic-crm",
                                "Synthetic CRM Context Model",
                                true,
                                List.of("low", "medium", "high"),
                                "medium",
                                100,
                                1_000
                        ))
                ),
                effectiveTierReader
        );
    }

    private AnalysisReport report(String reportId) {
        return new AnalysisReport(
                reportId,
                "Header",
                "Sub header",
                "Summary",
                List.of(),
                AnalysisReportMeta.empty()
        );
    }

    private AssistantMessageEvent assistantMessage(String content) {
        var event = new AssistantMessageEvent();
        event.setData(new AssistantMessageEvent.AssistantMessageEventData(
                "message-1", null, content, null,
                null, null, null, null, null,
                null, null, null,
                "interaction-1", null, null, null, null, null, null, null, null, null
        ));
        return event;
    }

    private AssistantMessageEvent assistantMessageWithReasoning(
            String content,
            String reasoningText,
        List<AssistantMessageToolRequest> toolRequests
    ) {
        var event = new AssistantMessageEvent();
        event.setData(new AssistantMessageEvent.AssistantMessageEventData(
                "message-reasoning", null, content, toolRequests,
                null, reasoningText, null, null, null,
                null, null, null,
                "interaction-reasoning", null, null, null, null, null, null, null, "reasoning-1", null
        ));
        return event;
    }

    private AssistantReasoningEvent assistantReasoning(String reasoningId, String content) {
        var event = new AssistantReasoningEvent();
        event.setData(new AssistantReasoningEvent.AssistantReasoningEventData(reasoningId, content, null));
        return event;
    }

    private AssistantUsageEvent assistantUsage(
            String model,
            Double inputTokens,
            Double outputTokens,
            Double cacheReadTokens,
            Double cacheWriteTokens,
            Double cost,
            Double duration
    ) {
        var event = new AssistantUsageEvent();
        event.setData(new AssistantUsageEvent.AssistantUsageEventData(
                model,
                inputTokens != null ? inputTokens.longValue() : null,
                outputTokens != null ? outputTokens.longValue() : null,
                cacheReadTokens != null ? cacheReadTokens.longValue() : null,
                cacheWriteTokens != null ? cacheWriteTokens.longValue() : null,
                null,
                null,
                cost,
                duration != null ? duration.longValue() : null,
                null, null, null, null, null, null, null, null, null, null,
                Map.of(),
                null, null, null, null, null,
                Map.of(),
                null, null
        ));
        return event;
    }

    private SessionUsageInfoEvent sessionUsageInfo(
            double tokenLimit,
            double currentTokens,
            double messagesLength
    ) {
        var event = new SessionUsageInfoEvent();
        event.setData(new SessionUsageInfoEvent.SessionUsageInfoEventData(
                Math.round(tokenLimit),
                Math.round(currentTokens),
                Math.round(messagesLength),
                null,
                null,
                null,
                null
        ));
        return event;
    }

    private SessionCompactionStartEvent compactionStart(
            Long systemTokens,
            Long conversationTokens,
            Long toolDefinitionsTokens
    ) {
        var event = new SessionCompactionStartEvent();
        event.setData(new SessionCompactionStartEvent.SessionCompactionStartEventData(
                null,
                systemTokens,
                conversationTokens,
                toolDefinitionsTokens,
                null,
                null,
                null
        ));
        return event;
    }
}
