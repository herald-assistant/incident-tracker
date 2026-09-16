package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.generated.rpc.RpcCaller;
import com.github.copilot.generated.rpc.SessionOptionsUpdateResult;
import com.github.copilot.generated.rpc.SessionRpc;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotContextTierActivatorTest {

    @Test
    void shouldSendNarrowLongContextPatchForCurrentCrmSession() {
        var capturedParams = new AtomicReference<Object>();
        RpcCaller caller = new RpcCaller() {
            @Override
            public <T> CompletableFuture<T> invoke(String method, Object params, Class<T> resultType) {
                assertThat(method).isEqualTo("session.options.update");
                capturedParams.set(params);
                return CompletableFuture.completedFuture(resultType.cast(
                        new SessionOptionsUpdateResult(true, 0L)
                ));
            }
        };

        var activated = new CopilotContextTierActivator().activateLongContext(
                new SessionRpc(caller, "synthetic-crm-session"),
                1_000L
        );

        assertThat(activated).isTrue();
        var payload = new ObjectMapper().valueToTree(capturedParams.get());
        assertThat(payload.path("sessionId").asText()).isEqualTo("synthetic-crm-session");
        assertThat(payload.path("contextTier").asText()).isEqualTo("long_context");
        assertThat(payload.size()).isEqualTo(2);
    }

    @Test
    void shouldReportRejectedLongContextPatch() {
        RpcCaller caller = new RpcCaller() {
            @Override
            public <T> CompletableFuture<T> invoke(String method, Object params, Class<T> resultType) {
                return CompletableFuture.completedFuture(resultType.cast(
                        new SessionOptionsUpdateResult(false, 0L)
                ));
            }
        };

        assertThat(new CopilotContextTierActivator().activateLongContext(
                new SessionRpc(caller, "synthetic-crm-session"),
                1_000L
        )).isFalse();
    }
}
