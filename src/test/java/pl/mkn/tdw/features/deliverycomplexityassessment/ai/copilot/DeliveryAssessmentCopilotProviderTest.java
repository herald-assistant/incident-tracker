package pl.mkn.tdw.features.deliverycomplexityassessment.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryAiResponseParser;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryPromptPreparation;
import pl.mkn.tdw.features.deliverycomplexityassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
                responseParser
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
}
