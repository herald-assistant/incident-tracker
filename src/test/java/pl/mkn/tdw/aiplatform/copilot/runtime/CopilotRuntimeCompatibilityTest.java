package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.GetStatusResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotRuntimeCompatibilityTest {

    private final CopilotRuntimeCompatibility compatibility = new CopilotRuntimeCompatibility();

    @Test
    void shouldAcceptCliSchemaThatExposesContextTier() {
        var info = compatibility.inspect(client("1.0.57-5", 3));

        assertThat(info).isEqualTo(new CopilotRuntimeVersionInfo(
                "1.0.11",
                "1.0.57-5",
                3,
                "1.0.57",
                true
        ));
    }

    @Test
    void shouldRejectOlderCliSchema() {
        var info = compatibility.inspect(client("1.0.56-9", 3));

        assertThat(info.compatible()).isFalse();
    }

    private CopilotClient client(String version, int protocolVersion) {
        var client = mock(CopilotClient.class);
        var status = new GetStatusResponse()
                .setVersion(version)
                .setProtocolVersion(protocolVersion);
        when(client.getStatus()).thenReturn(CompletableFuture.completedFuture(status));
        return client;
    }
}
