package pl.mkn.tdw.api.aioptions;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsResponse;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelPricing;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelTokenRates;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotAnalysisAiModelOptionsProviderTest {

    @Test
    void mapsLiveTokenRatesToOperatorApi() {
        var sdkOptions = mock(CopilotModelOptionsProvider.class);
        when(sdkOptions.modelOptions(any())).thenReturn(new CopilotModelOptionsResponse(
                "crm-versatile", "", List.of(), List.of(new CopilotModelOption(
                        "crm-versatile", "CRM Versatile", false, List.of(), "", 0, 0,
                        "versatile",
                        new CopilotModelPricing(
                                new CopilotModelTokenRates(200D, 20D, 250D, 1_200D),
                                new CopilotModelTokenRates(400D, 40D, 500D, 1_800D),
                                272_000L
                        )
                ))
        ));
        var provider = new CopilotAnalysisAiModelOptionsProvider(
                sdkOptions, () -> AnalysisAiAuthRef.localToken("CRM Workspace"), new CopilotRunAuthMapper()
        );

        var pricing = provider.modelOptions().models().get(0).pricing();

        assertEquals(200D, pricing.defaultRates().input());
        assertEquals(250D, pricing.defaultRates().cacheWrite());
        assertEquals(1_800D, pricing.longContextRates().output());
        assertEquals(272_000L, pricing.longContextThresholdTokens());
    }
}
