package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import com.github.copilot.generated.rpc.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
class CopilotSdkLiveModelLister implements CopilotSdkModelLister {

    private final CopilotSessionConfigFactory sessionConfigFactory;
    private final CopilotSdkProperties properties;
    private final CopilotClientShutdown clientShutdown;

    @Override
    public List<Model> listModels(CopilotRunAuth auth) {
        var timeout = timeout();
        try (var client = new CopilotClient(sessionConfigFactory.clientOptions(auth))) {
            try {
                client.start().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                var result = client.getRpc().models.list().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                return result != null && result.models() != null ? result.models() : List.of();
            } finally {
                clientShutdown.stop(client, "model-options");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while listing Copilot models.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to list Copilot models.", exception);
        }
    }

    private Duration timeout() {
        return properties.getModelOptionsTimeout() != null
                ? properties.getModelOptionsTimeout()
                : Duration.ofSeconds(20);
    }
}
