package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.github.copilot.sdk.ConnectionState;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.AssistantMessageEvent;
import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparedRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.executionGateway;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceCaptureRegistry;

class CopilotSdkExecutionGatewayTest {

    @Test
    void shouldUseDefaultSendAndWaitTimeoutFromProperties() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceCaptureRegistry(new com.fasterxml.jackson.databind.ObjectMapper()),
                metricsRegistry()
        );
        var preparedRequest = new CopilotSdkPreparedRequest(
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

            assertEquals("Structured answer", response);
            verify(sessionRef.get()).sendAndWait(same(preparedRequest.messageOptions()), eq(300_000L));
        }
    }

    @Test
    void shouldUseConfiguredSendAndWaitTimeout() {
        var properties = new CopilotSdkProperties();
        properties.setSendAndWaitTimeout(Duration.ofSeconds(90));
        var gateway = executionGateway(
                properties,
                toolEvidenceCaptureRegistry(new com.fasterxml.jackson.databind.ObjectMapper()),
                metricsRegistry()
        );
        var preparedRequest = new CopilotSdkPreparedRequest(
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

            assertEquals("Configured timeout answer", response);
            verify(sessionRef.get()).sendAndWait(same(preparedRequest.messageOptions()), eq(90_000L));
        }
    }

    @Test
    void shouldExecuteWithArtifactOnlyPreparedRequest() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceCaptureRegistry(new com.fasterxml.jackson.databind.ObjectMapper()),
                metricsRegistry()
        );

        var preparedRequest = new CopilotSdkPreparedRequest(
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
            assertEquals("Structured answer", gateway.execute(preparedRequest));
        }
    }

    @Test
    void shouldNotClosePreparedRequestAfterExecution() {
        var properties = new CopilotSdkProperties();
        var gateway = executionGateway(
                properties,
                toolEvidenceCaptureRegistry(new com.fasterxml.jackson.databind.ObjectMapper()),
                metricsRegistry()
        );
        var messageOptions = new MessageOptions().setPrompt("Diagnose incident");
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparedRequest.correlationId()).thenReturn("corr-gateway-owned");
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
            assertEquals("Structured answer", gateway.execute(preparedRequest));
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

    private CopilotSessionMetricsRegistry metricsRegistry() {
        return new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
    }
}
