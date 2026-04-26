package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparedRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityGate;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.response.CopilotResponseParser;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CopilotSdkAnalysisAiProviderPreparedFlowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAnalyzeAlreadyPreparedRequestWithoutPreparingAgain() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);
        var request = new AnalysisAiAnalysisRequest("corr-prepared", "zt01", "main", "sample/runtime", List.of());
        var preparedRequest = new CopilotSdkPreparedRequest(
                request.correlationId(),
                new CopilotClientOptions(),
                new SessionConfig().setSessionId("analysis-prepared"),
                new MessageOptions().setPrompt("Prepared prompt body"),
                "Prepared prompt body",
                Map.of(),
                request
        );

        when(executionGateway.execute(same(preparedRequest), same(AnalysisAiToolEvidenceListener.NO_OP)))
                .thenReturn("""
                        {
                          "detectedProblem": "PREPARED_RESPONSE",
                          "summary": "Prepared request executed.",
                          "recommendedAction": "Keep using the prepared request.",
                          "rationale": "The provider received a prepared request directly.",
                          "affectedFunction": "Execution of the already prepared Copilot analysis request.",
                          "affectedProcess": "Prepared analysis",
                          "affectedBoundedContext": "AI Runtime",
                          "affectedTeam": "AI Platform",
                          "confidence": "medium",
                          "evidenceReferences": [],
                          "visibilityLimits": []
                        }
                        """);

        var response = provider.analyze(preparedRequest, AnalysisAiToolEvidenceListener.NO_OP);

        assertEquals("PREPARED_RESPONSE", response.detectedProblem());
        assertEquals("Prepared prompt body", response.prompt());
        verifyNoInteractions(preparationService);
        verify(executionGateway).execute(same(preparedRequest), same(AnalysisAiToolEvidenceListener.NO_OP));
    }

    private CopilotSdkAnalysisAiProvider provider(
            CopilotSdkPreparationService preparationService,
            CopilotSdkExecutionGateway executionGateway
    ) {
        var metricsProperties = new CopilotMetricsProperties();
        var registry = new CopilotSessionMetricsRegistry(metricsProperties);
        return new CopilotSdkAnalysisAiProvider(
                preparationService,
                executionGateway,
                new CopilotResponseParser(objectMapper),
                new CopilotResponseQualityGate(new CopilotResponseQualityProperties()),
                registry,
                new CopilotMetricsLogger(metricsProperties, objectMapper)
        );
    }
}
