package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotClientShutdownTest {

    @Test
    void shouldUseGracefulStopWhenItCompletes() {
        var client = mock(CopilotClient.class);
        when(client.stop()).thenReturn(CompletableFuture.completedFuture(null));

        new CopilotClientShutdown(new CopilotSdkProperties()).stop(client, "run-1");

        verify(client).stop();
        verify(client, never()).forceStop();
    }

    @Test
    void shouldForceStopWhenGracefulStopFails() {
        var client = mock(CopilotClient.class);
        when(client.stop()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("stop failed")));
        when(client.forceStop()).thenReturn(CompletableFuture.completedFuture(null));

        new CopilotClientShutdown(new CopilotSdkProperties()).stop(client, "run-2");

        verify(client).stop();
        verify(client).forceStop();
    }

    @Test
    void shouldFailWhenGracefulAndForcedStopFail() {
        var client = mock(CopilotClient.class);
        when(client.stop()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("stop failed")));
        when(client.forceStop()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("force failed")));

        assertThrows(
                IllegalStateException.class,
                () -> new CopilotClientShutdown(new CopilotSdkProperties()).stop(client, "run-3")
        );
    }
}
