package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.SessionConfig;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotSdkAnalysisAiProviderJsonResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapJsonResponseToCurrentPublicAiResponse() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);
        var request = new AnalysisAiAnalysisRequest("corr-json", "zt01", "main", "sample/runtime", List.of());
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparationService.prepare(request)).thenReturn(preparedRequest);
        when(preparedRequest.prompt()).thenReturn("Prepared JSON prompt");
        when(preparedRequest.sessionConfig()).thenReturn(new SessionConfig().setSessionId("analysis-json"));
        when(executionGateway.execute(preparedRequest)).thenReturn("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "summary": "Timeout w `CatalogClient`.",
                  "recommendedAction": "- Sprawdz latency downstream.",
                  "rationale": "- Log i kod wskazuja klient HTTP.",
                  "affectedFunction": "Pobranie katalogu w flow zamowienia.",
                  "affectedProcess": "Zamowienia",
                  "affectedBoundedContext": "Ordering",
                  "affectedTeam": "Orders Team",
                  "confidence": "high",
                  "evidenceReferences": [],
                  "visibilityLimits": []
                }
                """);

        var response = provider.analyze(request);

        assertEquals("copilot-sdk", response.providerName());
        assertEquals("DOWNSTREAM_TIMEOUT", response.detectedProblem());
        assertEquals("Timeout w `CatalogClient`.", response.summary());
        assertEquals("- Sprawdz latency downstream.", response.recommendedAction());
        assertEquals("Pobranie katalogu w flow zamowienia.", response.affectedFunction());
        assertEquals("Zamowienia", response.affectedProcess());
        assertEquals("Ordering", response.affectedBoundedContext());
        assertEquals("Orders Team", response.affectedTeam());
        assertEquals("Prepared JSON prompt", response.prompt());
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
