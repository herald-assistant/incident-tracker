package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.github.copilot.sdk.ConnectionState;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.AssistantMessageEvent;
import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotAttachmentArtifactBundle;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparedRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolEvidenceCaptureRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotSdkExecutionGatewayTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldUseDefaultSendAndWaitTimeoutFromProperties() {
        var properties = new CopilotSdkProperties();
        var gateway = new CopilotSdkExecutionGateway(
                properties,
                new CopilotToolEvidenceCaptureRegistry(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var preparedRequest = new CopilotSdkPreparedRequest(
                "corr-123",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                CopilotAttachmentArtifactBundle.empty()
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
        var gateway = new CopilotSdkExecutionGateway(
                properties,
                new CopilotToolEvidenceCaptureRegistry(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var preparedRequest = new CopilotSdkPreparedRequest(
                "corr-456",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                CopilotAttachmentArtifactBundle.empty()
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
    void shouldKeepLegacyStagingDirectoryUntouchedWhenBundleUsesInlineAttachments() throws Exception {
        var properties = new CopilotSdkProperties();
        var gateway = new CopilotSdkExecutionGateway(
                properties,
                new CopilotToolEvidenceCaptureRegistry(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        var artifactDirectory = Files.createDirectory(tempDirectory.resolve("attachments"));
        Files.writeString(artifactDirectory.resolve("00-incident-manifest.json"), "{}");

        var preparedRequest = new CopilotSdkPreparedRequest(
                "corr-cleanup",
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt("Diagnose incident"),
                "Diagnose incident",
                new CopilotAttachmentArtifactBundle(List.of(), artifactDirectory)
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

        assertTrue(Files.exists(artifactDirectory));
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
}
