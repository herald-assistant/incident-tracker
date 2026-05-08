package pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution;

import com.github.copilot.sdk.ConnectionState;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.AbstractSessionEvent;
import com.github.copilot.sdk.events.AssistantMessageEvent;
import com.github.copilot.sdk.events.AssistantReasoningEvent;
import com.github.copilot.sdk.events.AssistantUsageEvent;
import com.github.copilot.sdk.events.SessionUsageInfoEvent;
import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.testsupport.copilot.CopilotTestFixtures.executionGateway;
import static pl.mkn.incidenttracker.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

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
        var eventHandler = new AtomicReference<Consumer<AbstractSessionEvent>>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn(sessionId);
            when(session.on(isA(Consumer.class))).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                var handler = (Consumer<AbstractSessionEvent>) invocation.getArgument(0);
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
        var eventHandler = new AtomicReference<Consumer<AbstractSessionEvent>>();

        try (MockedConstruction<CopilotClient> ignored = mockConstruction(CopilotClient.class, (client, context) -> {
            var session = mock(CopilotSession.class);

            when(client.getState()).thenReturn(ConnectionState.CONNECTED);
            when(client.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(client.createSession(any(SessionConfig.class))).thenReturn(CompletableFuture.completedFuture(session));
            when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getSessionId()).thenReturn(sessionId);
            when(session.on(isA(Consumer.class))).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                var handler = (Consumer<AbstractSessionEvent>) invocation.getArgument(0);
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
                                List.of(new AssistantMessageEvent.AssistantMessageData.ToolRequest(
                                        "tool-call-1",
                                        "gitlab_find_class_references",
                                        Map.of("reason", "Sprawdzam klasę z stack trace.")
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

    private AssistantMessageEvent assistantMessage(String content) {
        var event = new AssistantMessageEvent();
        event.setData(new AssistantMessageEvent.AssistantMessageData(
                "message-1",
                content,
                null,
                null,
                "interaction-1",
                null,
                null,
                null
        ));
        return event;
    }

    private AssistantMessageEvent assistantMessageWithReasoning(
            String content,
            String reasoningText,
            List<AssistantMessageEvent.AssistantMessageData.ToolRequest> toolRequests
    ) {
        var event = new AssistantMessageEvent();
        event.setData(new AssistantMessageEvent.AssistantMessageData(
                "message-reasoning",
                content,
                toolRequests,
                null,
                "interaction-reasoning",
                "reasoning-1",
                reasoningText,
                null
        ));
        return event;
    }

    private AssistantReasoningEvent assistantReasoning(String reasoningId, String content) {
        var event = new AssistantReasoningEvent();
        event.setData(new AssistantReasoningEvent.AssistantReasoningData(
                reasoningId,
                content
        ));
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
        event.setData(new AssistantUsageEvent.AssistantUsageData(
                model,
                inputTokens,
                outputTokens,
                cacheReadTokens,
                cacheWriteTokens,
                cost,
                duration,
                null,
                null,
                null,
                null,
                Map.of(),
                null
        ));
        return event;
    }

    private SessionUsageInfoEvent sessionUsageInfo(
            double tokenLimit,
            double currentTokens,
            double messagesLength
    ) {
        var event = new SessionUsageInfoEvent();
        event.setData(new SessionUsageInfoEvent.SessionUsageInfoData(
                tokenLimit,
                currentTokens,
                messagesLength
        ));
        return event;
    }
}
