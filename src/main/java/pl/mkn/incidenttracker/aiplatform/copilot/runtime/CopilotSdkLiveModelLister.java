package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.ModelInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
class CopilotSdkLiveModelLister implements CopilotSdkModelLister {

    private final CopilotSessionConfigFactory sessionConfigFactory;
    private final CopilotSdkProperties properties;

    @Override
    public List<ModelInfo> listModels(CopilotRunAuth auth) {
        var timeout = timeout();
        try (var client = new CopilotClient(sessionConfigFactory.clientOptions(auth))) {
            client.start().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return client.listModels().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
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
