package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.SessionConfig;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotInitialAnalysisPreparation;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.quality.CopilotResponseQualityGate;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.quality.CopilotResponseQualityProperties;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.response.CopilotResponseParser;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session.CopilotMetricsLogger;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session.CopilotMetricsProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.CopilotInitialAnalysisProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.sessionTelemetry;

class CopilotInitialAnalysisProviderJsonResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapJsonResponseToCurrentPublicAiResponse() {
        var preparationService = mock(CopilotIncidentInitialPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);
        var request = new InitialAnalysisRequest("corr-json", "zt01", "main", "sample/runtime", List.of());
        var preparedRequest = mock(CopilotPreparedSession.class);

        when(preparationService.prepare(request)).thenReturn(new CopilotInitialAnalysisPreparation(request, preparedRequest));
        when(preparedRequest.prompt()).thenReturn("Prepared JSON prompt");
        when(preparedRequest.sessionConfig()).thenReturn(new SessionConfig().setSessionId("analysis-json"));
        when(executionGateway.execute(same(preparedRequest))).thenReturn("""
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

        var response = analyze(provider, request);

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

    private CopilotInitialAnalysisProvider provider(
            CopilotIncidentInitialPreparationService preparationService,
            CopilotSdkExecutionGateway executionGateway
    ) {
        var metricsProperties = new CopilotMetricsProperties();
        var registry = new CopilotSessionMetricsRegistry(metricsProperties);
        return new CopilotInitialAnalysisProvider(
                preparationService,
                executionGateway,
                new CopilotResponseParser(objectMapper),
                new CopilotResponseQualityGate(new CopilotResponseQualityProperties()),
                sessionTelemetry(registry, new CopilotMetricsLogger(metricsProperties, objectMapper))
        );
    }

    private InitialAnalysisResponse analyze(CopilotInitialAnalysisProvider provider, InitialAnalysisRequest request) {
        try (var prepared = provider.prepare(request)) {
            return provider.analyze(prepared, AnalysisAiToolEvidenceListener.NO_OP);
        }
    }
}
