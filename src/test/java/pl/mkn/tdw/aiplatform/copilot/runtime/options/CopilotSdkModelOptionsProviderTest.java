package pl.mkn.tdw.aiplatform.copilot.runtime.options;

import com.github.copilot.generated.rpc.Model;
import com.github.copilot.generated.rpc.ModelBilling;
import com.github.copilot.generated.rpc.ModelBillingTokenPrices;
import com.github.copilot.generated.rpc.ModelBillingTokenPricesLongContext;
import com.github.copilot.generated.rpc.ModelCapabilities;
import com.github.copilot.generated.rpc.ModelCapabilitiesLimits;
import com.github.copilot.generated.rpc.ModelCapabilitiesSupports;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkModelLister;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSdkModelOptionsProviderTest {

    @Test
    void shouldMapSdkModelReasoningMetadata() {
        var properties = new CopilotSdkProperties();
        properties.setModel("crm-reasoning-model");
        properties.setReasoningEffort("medium");
        var provider = new CopilotSdkModelOptionsProvider(
                auth -> List.of(
                        reasoningModel(
                                "crm-reasoning-model",
                                "Synthetic CRM Reasoning Model",
                                List.of("low", "medium", "high"),
                                "medium"
                        ),
                        plainModel("crm-basic-model", "Synthetic CRM Basic Model")
                ),
                properties
        );

        var response = provider.modelOptions(CopilotRunAuth.localToken());

        assertEquals("crm-reasoning-model", response.defaultModel());
        assertEquals("medium", response.defaultReasoningEffort());
        assertEquals(List.of("low", "medium", "high"), response.defaultReasoningEfforts());
        assertEquals(2, response.models().size());
        assertEquals("crm-reasoning-model", response.models().get(0).id());
        assertEquals("Synthetic CRM Reasoning Model", response.models().get(0).name());
        assertTrue(response.models().get(0).supportsReasoningEffort());
        assertEquals(List.of("low", "medium", "high"), response.models().get(0).reasoningEfforts());
        assertEquals("medium", response.models().get(0).defaultReasoningEffort());
        assertEquals(100, response.models().get(0).defaultContextWindowTokens());
        assertEquals(1_000, response.models().get(0).longContextWindowTokens());
        assertTrue(response.models().get(0).supportsLongContext());
        assertFalse(response.models().get(1).supportsReasoningEffort());
        assertEquals(List.of(), response.models().get(1).reasoningEfforts());
        assertFalse(response.models().get(1).supportsLongContext());
    }

    @Test
    void shouldReturnConfiguredDefaultsWhenSdkModelsAreUnavailable() {
        var properties = new CopilotSdkProperties();
        properties.setModel("crm-reasoning-model");
        properties.setReasoningEffort("medium");
        var provider = new CopilotSdkModelOptionsProvider(
                auth -> {
                    throw new IllegalStateException("CLI unavailable");
                },
                properties
        );

        var response = provider.modelOptions(CopilotRunAuth.localToken());

        assertEquals("crm-reasoning-model", response.defaultModel());
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

        provider.modelOptions(CopilotRunAuth.localToken());
        provider.modelOptions(CopilotRunAuth.localToken());

        assertEquals(1, lister.calls);
    }

    private static Model reasoningModel(
            String id,
            String name,
            List<String> reasoningEfforts,
            String defaultReasoningEffort
    ) {
        return new Model(
                id,
                name,
                new ModelCapabilities(
                        new ModelCapabilitiesSupports(false, true),
                        new ModelCapabilitiesLimits(980L, 20L, 1_000L, null)
                ),
                null,
                new ModelBilling(1D, new ModelBillingTokenPrices(
                        1D,
                        1D,
                        1D,
                        1_000L,
                        80L,
                        new ModelBillingTokenPricesLongContext(2D, 2D, 2D, 980L)
                )),
                reasoningEfforts,
                defaultReasoningEffort,
                null,
                null
        );
    }

    private static Model plainModel(String id, String name) {
        return new Model(
                id,
                name,
                new ModelCapabilities(new ModelCapabilitiesSupports(false, false), null),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static final class CountingModelLister implements CopilotSdkModelLister {

        private int calls;

        @Override
        public List<Model> listModels(CopilotRunAuth auth) {
            calls++;
            return List.of(plainModel("crm-basic-model", "Synthetic CRM Basic Model"));
        }
    }
}
