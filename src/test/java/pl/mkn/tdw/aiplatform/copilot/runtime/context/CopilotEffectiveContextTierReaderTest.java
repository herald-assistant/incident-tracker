package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.CopilotSession;
import com.github.copilot.generated.rpc.ContextTier;
import com.github.copilot.generated.rpc.RpcCaller;
import com.github.copilot.generated.rpc.SessionModelGetCurrentResult;
import com.github.copilot.generated.rpc.SessionRpc;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotEffectiveContextTierReaderTest {

    @Test
    void shouldReadEffectiveCrmTierFromTypedSessionRpc() {
        RpcCaller caller = new RpcCaller() {
            @Override
            public <T> CompletableFuture<T> invoke(String method, Object params, Class<T> resultType) {
                assertThat(method).isEqualTo("session.model.getCurrent");
                var result = new SessionModelGetCurrentResult(
                        "gpt-synthetic-crm",
                        "high",
                        ContextTier.LONG_CONTEXT
                );
                return CompletableFuture.completedFuture(resultType.cast(result));
            }
        };
        var session = mock(CopilotSession.class);
        when(session.getRpc()).thenReturn(new SessionRpc(caller, "synthetic-crm-session"));

        var effective = new CopilotEffectiveContextTierReader(new CopilotSdkProperties()).read(session);

        assertThat(effective).isEqualTo(new CopilotEffectiveContextTier(
                "gpt-synthetic-crm",
                "high",
                "long_context"
        ));
    }
}
