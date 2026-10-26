package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.generated.rpc.AdaptiveThinkingSupport;
import com.github.copilot.generated.rpc.ModelsListResult;
import com.github.copilot.rpc.CopilotClientOptions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotSdkLiveModelListerTest {

    @Test
    void shouldDeserializeModelCatalogWithAdaptiveOnlyThinking() throws Exception {
        var result = new ObjectMapper().readValue("""
                {
                  "models": [{
                    "id": "crm-analysis-model",
                    "name": "CRM Analysis Model",
                    "capabilities": {
                      "supports": { "adaptive_thinking": "adaptive_only" }
                    }
                  }]
                }
                """, ModelsListResult.class);

        assertThat(result.models()).hasSize(1);
        var model = result.models().get(0);
        assertThat(model.id()).isEqualTo("crm-analysis-model");
        assertThat(model.capabilities().supports().adaptiveThinking())
                .isEqualTo(AdaptiveThinkingSupport.ADAPTIVE_ONLY);
    }

    @Test
    void shouldStopClientWhenStartFails() {
        var sessionConfigFactory = mock(CopilotSessionConfigFactory.class);
        var properties = new CopilotSdkProperties();
        var clientShutdown = mock(CopilotClientShutdown.class);
        var auth = mock(CopilotRunAuth.class);
        when(sessionConfigFactory.clientOptions(auth)).thenReturn(new CopilotClientOptions());

        try (MockedConstruction<CopilotClient> construction = mockConstruction(
                CopilotClient.class,
                (client, context) -> when(client.start()).thenReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("start failed"))
                )
        )) {
            var modelLister = new CopilotSdkLiveModelLister(sessionConfigFactory, properties, clientShutdown);

            assertThrows(IllegalStateException.class, () -> modelLister.listModels(auth));

            verify(clientShutdown).stop(construction.constructed().get(0), "model-options");
        }
    }
}
