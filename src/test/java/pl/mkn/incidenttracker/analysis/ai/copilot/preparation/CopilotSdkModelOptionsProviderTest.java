package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ModelCapabilities;
import com.github.copilot.sdk.json.ModelInfo;
import com.github.copilot.sdk.json.ModelSupports;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSdkModelOptionsProviderTest {

    @Test
    void shouldMapSdkModelReasoningMetadata() {
        var properties = new CopilotSdkProperties();
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        var provider = new CopilotSdkModelOptionsProvider(
                () -> List.of(
                        reasoningModel("gpt-5.4", "GPT-5.4", List.of("low", "medium", "high"), "medium"),
                        plainModel("gpt-5.4-mini", "GPT-5.4 Mini")
                ),
                properties
        );

        var response = provider.modelOptions();

        assertEquals("gpt-5.4", response.defaultModel());
        assertEquals("medium", response.defaultReasoningEffort());
        assertEquals(List.of("low", "medium", "high"), response.defaultReasoningEfforts());
        assertEquals(2, response.models().size());
        assertEquals("gpt-5.4", response.models().get(0).id());
        assertEquals("GPT-5.4", response.models().get(0).name());
        assertTrue(response.models().get(0).supportsReasoningEffort());
        assertEquals(List.of("low", "medium", "high"), response.models().get(0).reasoningEfforts());
        assertEquals("medium", response.models().get(0).defaultReasoningEffort());
        assertFalse(response.models().get(1).supportsReasoningEffort());
        assertEquals(List.of(), response.models().get(1).reasoningEfforts());
    }

    @Test
    void shouldReturnConfiguredDefaultsWhenSdkModelsAreUnavailable() {
        var properties = new CopilotSdkProperties();
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        var provider = new CopilotSdkModelOptionsProvider(
                () -> {
                    throw new IllegalStateException("CLI unavailable");
                },
                properties
        );

        var response = provider.modelOptions();

        assertEquals("gpt-5.4", response.defaultModel());
        assertEquals("medium", response.defaultReasoningEffort());
        assertEquals(List.of(), response.defaultReasoningEfforts());
        assertEquals(List.of(), response.models());
    }

    @Test
    void shouldCacheSuccessfulSdkModels() {
        var properties = new CopilotSdkProperties();
        properties.setModelOptionsCacheTtl(Duration.ofMinutes(5));
        var lister = new CountingModelLister();
        var provider = new CopilotSdkModelOptionsProvider(lister, properties);

        provider.modelOptions();
        provider.modelOptions();

        assertEquals(1, lister.calls);
    }

    private ModelInfo reasoningModel(
            String id,
            String name,
            List<String> reasoningEfforts,
            String defaultReasoningEffort
    ) {
        return new ModelInfo()
                .setId(id)
                .setName(name)
                .setSupportedReasoningEfforts(reasoningEfforts)
                .setDefaultReasoningEffort(defaultReasoningEffort)
                .setCapabilities(new ModelCapabilities().setSupports(
                        new ModelSupports().setReasoningEffort(true)
                ));
    }

    private ModelInfo plainModel(String id, String name) {
        return new ModelInfo()
                .setId(id)
                .setName(name)
                .setCapabilities(new ModelCapabilities().setSupports(
                        new ModelSupports().setReasoningEffort(false)
                ));
    }

    private static final class CountingModelLister implements CopilotSdkModelLister {

        private int calls;

        @Override
        public List<ModelInfo> listModels() {
            calls++;
            return List.of(new ModelInfo().setId("gpt-5.4").setName("GPT-5.4"));
        }
    }
}
