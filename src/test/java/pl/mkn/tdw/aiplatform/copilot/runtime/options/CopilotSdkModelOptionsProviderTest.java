package pl.mkn.tdw.aiplatform.copilot.runtime.options;

import com.github.copilot.generated.rpc.Model;
import com.github.copilot.generated.rpc.ModelBilling;
import com.github.copilot.generated.rpc.ModelBillingTokenPrices;
import com.github.copilot.generated.rpc.ModelBillingTokenPricesLongContext;
import com.github.copilot.generated.rpc.ModelCapabilities;
import com.github.copilot.generated.rpc.ModelCapabilitiesLimits;
import com.github.copilot.generated.rpc.ModelCapabilitiesSupports;
import com.github.copilot.generated.rpc.ModelPickerCategory;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkModelLister;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
                                List.of("low", "medium", "high")
                        ),
                        plainModel("crm-basic-model", "Synthetic CRM Basic Model")
                ),
                properties,
                () -> "github_pat_crm_test_token"
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
        assertEquals("", response.models().get(0).defaultReasoningEffort());
        assertEquals(100, response.models().get(0).defaultContextWindowTokens());
        assertEquals(1_000, response.models().get(0).longContextWindowTokens());
        assertTrue(response.models().get(0).supportsLongContext());
        assertEquals("versatile", response.models().get(0).modelPickerCategory());
        assertEquals(200D, response.models().get(0).pricing().defaultRates().input());
        assertEquals(20D, response.models().get(0).pricing().defaultRates().cachedInput());
        assertEquals(250D, response.models().get(0).pricing().defaultRates().cacheWrite());
        assertEquals(1_200D, response.models().get(0).pricing().defaultRates().output());
        assertEquals(400D, response.models().get(0).pricing().longContextRates().input());
        assertEquals(80L, response.models().get(0).pricing().longContextThresholdTokens());
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
                properties,
                () -> "github_pat_crm_test_token"
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
        var pat = new AtomicReference<>("github_pat_crm_test_token");
        var provider = new CopilotSdkModelOptionsProvider(lister, properties, pat::get);

        provider.modelOptions(CopilotRunAuth.localToken());
        provider.modelOptions(CopilotRunAuth.localToken());

        assertEquals(1, lister.calls);

        pat.set("github_pat_crm_rotated_token");
        provider.modelOptions(CopilotRunAuth.localToken());
        assertEquals(2, lister.calls);
    }

    @Test
    void shouldRetainOrdinaryPromptAndOutputLimitsWithoutLongContextBilling() {
        var model = new Model("crm-basic-model", "CRM model",
                new ModelCapabilities(new ModelCapabilitiesSupports(false, false, null),
                        new ModelCapabilitiesLimits(500L, 100L, 600L, null)), null, null, null, null, null);
        var provider = new CopilotSdkModelOptionsProvider(auth -> List.of(model), new CopilotSdkProperties(), () -> "github_pat_crm_test_token");
        var option = provider.modelOptions(CopilotRunAuth.localToken()).models().get(0);
        assertEquals(500, option.defaultPromptTokens());
        assertEquals(100, option.maxOutputTokens());
        assertEquals(600, option.defaultContextWindowTokens());
        assertFalse(option.supportsLongContext());
    }

    @Test
    void shouldPreferExplicitPromptBudgetsOverBillingContextThresholds() {
        var model = new Model("crm-large-model", "CRM model",
                new ModelCapabilities(new ModelCapabilitiesSupports(false, false, null),
                        new ModelCapabilitiesLimits(980L, 20L, 1000L, null)), null,
                new ModelBilling(1D, new ModelBillingTokenPrices(.2, .4, null, null, null, 1000L, 90L, 80L,
                        new ModelBillingTokenPricesLongContext(.4, .8, null, null, null, 990L, 980L)), null, null), null, null, null);
        var provider = new CopilotSdkModelOptionsProvider(auth -> List.of(model), new CopilotSdkProperties(), () -> "github_pat_crm_test_token");
        var option = provider.modelOptions(CopilotRunAuth.localToken()).models().get(0);
        assertEquals(80, option.defaultPromptTokens());
        assertEquals(980, option.longPromptTokens());
        assertEquals(100, option.defaultContextWindowTokens());
        assertTrue(option.supportsLongContext());
    }

    private static Model reasoningModel(
            String id,
            String name,
            List<String> reasoningEfforts
    ) {
        return new Model(
                id,
                name,
                new ModelCapabilities(
                        new ModelCapabilitiesSupports(true, true, null),
                        new ModelCapabilitiesLimits(980L, 20L, 1_000L, null)
                ),
                null,
                new ModelBilling(1D, new ModelBillingTokenPrices(
                        0.2D,
                        1.2D,
                        0.02D,
                        null,
                        0.25D,
                        1_000L,
                        80L,
                        null,
                        new ModelBillingTokenPricesLongContext(0.4D, 1.8D, 0.04D, null, 0.5D, 980L, null)
                ), null, null),
                reasoningEfforts,
                ModelPickerCategory.VERSATILE,
                null
        );
    }

    private static Model plainModel(String id, String name) {
        return new Model(
                id,
                name,
                new ModelCapabilities(new ModelCapabilitiesSupports(false, false, null), null),
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
