package pl.mkn.tdw.features.deliverycomplexityassessment.ai.copilot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierPreference;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotInitialContextModelSelector;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsResponse;
import pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryComplexityAssessmentProperties;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryAiResponse;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryAiResponseParser;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryPromptPreparation;
import pl.mkn.tdw.features.deliverycomplexityassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryAssessmentCopilotProviderTest {

    @Test
    void shouldPublishRawResponseBeforeParsingFails() {
        var requestAssembler = mock(DeliveryAssessmentCopilotRunRequestAssembler.class);
        var runPreparationService = mock(CopilotRunPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var responseParser = mock(DeliveryAiResponseParser.class);
        var request = mock(CopilotRunRequest.class);
        var modelSelector = mock(CopilotInitialContextModelSelector.class);
        when(request.withActivitySink(any())).thenReturn(request);
        when(modelSelector.select(any(), any())).thenReturn(request);
        var session = mock(CopilotPreparedSession.class);
        var rawResponse = "not a valid assessment";
        var captured = new AtomicReference<String>();
        when(requestAssembler.assemble(anyString(), any(), any(), any())).thenReturn(request);
        when(runPreparationService.prepare(request)).thenReturn(session);
        when(executionGateway.execute(session)).thenReturn(new CopilotExecutionResult(rawResponse, null, "session-1"));
        when(responseParser.parse(rawResponse)).thenAnswer(invocation -> {
            assertThat(captured.get()).isEqualTo(rawResponse);
            throw new IllegalArgumentException("AI response did not contain JSON assessment.");
        });
        var provider = new DeliveryAssessmentCopilotProvider(
                requestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                modelSelector,
                new DeliveryComplexityAssessmentProperties()
        );

        assertThatThrownBy(() -> provider.analyze(
                "run-1",
                new AnalysisAiOptions("gpt-5", "medium"),
                AnalysisAiAuthRef.localToken("test"),
                mock(DeliveryEvidencePacket.class),
                new DeliveryPromptPreparation("prompt", Map.of()),
                AnalysisAiActivityListener.NO_OP,
                captured::set
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI response did not contain JSON assessment.");
    }

    @Test
    void shouldChooseModelsPerUnitBeforePreparingSessionsAndKeepPromptsUnchanged() {
        var preparationService = mock(CopilotRunPreparationService.class);
        var gateway = mock(CopilotSdkExecutionGateway.class);
        var parser = mock(DeliveryAiResponseParser.class);
        var properties = new DeliveryComplexityAssessmentProperties();
        properties.getAi().setLongContextModel("crm-extended");
        properties.getAi().setLongContextReasoningEffort("high");
        var sdkProperties = new CopilotSdkProperties();
        sdkProperties.getContextTier().setEstimatedCharactersPerToken(1);
        sdkProperties.getContextTier().setReservedTokens(10);
        var selector = new CopilotInitialContextModelSelector(sdkProperties, auth ->
                new CopilotModelOptionsResponse(null, null, List.of(), List.of(
                        new CopilotModelOption("crm-default", "CRM", true, List.of("low"), "low", 100, 0),
                        new CopilotModelOption("crm-extended", "CRM", true, List.of("high"), "high", 200, 1000)
                )));
        var events = new ArrayList<AnalysisAiActivityEvent>();
        var requests = new ArrayList<CopilotRunRequest>();
        var session = mock(CopilotPreparedSession.class);
        when(preparationService.prepare(any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            assertThat(events).hasSize(requests.size());
            return session;
        });
        when(gateway.execute(session)).thenReturn(new CopilotExecutionResult("crm-json", null, "crm-session"));
        when(parser.parse(anyString())).thenReturn(mock(DeliveryAiResponse.class));
        var provider = new DeliveryAssessmentCopilotProvider(
                new DeliveryAssessmentCopilotRunRequestAssembler(new CopilotRunAuthMapper()),
                preparationService, gateway, parser, selector, properties);
        for (var size : List.of(20, 120)) {
            provider.analyze("crm-run:" + size, new AnalysisAiOptions("crm-default", "low"),
                    AnalysisAiAuthRef.localToken("test"), mock(DeliveryEvidencePacket.class),
                    new DeliveryPromptPreparation("C".repeat(size), Map.of("crm.md", "inline evidence")),
                    events::add, response -> {});
        }
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).sessionConfigRequest().modelSelection().model()).isEqualTo("crm-default");
        assertThat(requests.get(0).sessionConfigRequest().modelSelection().reasoningEffort()).isEqualTo("low");
        assertThat(requests.get(1).sessionConfigRequest().modelSelection().model()).isEqualTo("crm-extended");
        assertThat(requests.get(1).sessionConfigRequest().modelSelection().reasoningEffort()).isEqualTo("high");
        assertThat(requests.get(1).sessionConfigRequest().contextTierPreference())
                .isEqualTo(CopilotContextTierPreference.LONG_CONTEXT_REQUIRED);
        assertThat(requests.get(0).prompt()).isEqualTo("C".repeat(20));
        assertThat(requests.get(1).prompt()).isEqualTo("C".repeat(120));
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.sessionConfigRequest().skillsEnabled()).isFalse();
            assertThat(request.sessionConfigRequest().effectiveAvailableToolNames()).isEmpty();
        });
    }
}
