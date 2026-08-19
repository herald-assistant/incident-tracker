package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.CopilotClientOptions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotSdkSessionCleanupTest {

    @Test
    void shouldStopClientAndDeleteLocalStateWhenStartFails() {
        var sessionConfigFactory = mock(CopilotSessionConfigFactory.class);
        var properties = new CopilotSdkProperties();
        var stateDirectoryCleaner = mock(CopilotSessionStateDirectoryCleaner.class);
        var clientShutdown = mock(CopilotClientShutdown.class);
        var auth = mock(CopilotRunAuth.class);
        when(sessionConfigFactory.clientOptions(auth)).thenReturn(new CopilotClientOptions());

        try (MockedConstruction<CopilotClient> construction = mockConstruction(
                CopilotClient.class,
                (client, context) -> when(client.start()).thenReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("start failed"))
                )
        )) {
            var cleanup = new CopilotSdkSessionCleanup(
                    sessionConfigFactory,
                    properties,
                    stateDirectoryCleaner,
                    clientShutdown
            );

            cleanup.deleteSession("session-1", auth);

            verify(clientShutdown).stop(construction.constructed().get(0), "session-cleanup:session-1");
            verify(stateDirectoryCleaner).deleteSessionStateDirectory("session-1");
        }
    }
}
