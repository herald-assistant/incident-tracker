package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.SessionConfig;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotInitialAnalysisPreparation;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.response.CopilotResponseParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        when(executionGateway.execute(same(preparedRequest))).thenReturn(executionResult("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "affectedProcess": "Zamowienia",
                  "affectedBoundedContext": "Ordering",
                  "affectedTeam": "Orders Team",
                  "functionalAnalysis": "Timeout dotyka pobrania katalogu w flow zamowienia.",
                  "technicalAnalysis": "- Sprawdz `CatalogClient` i latency downstream.",
                  "confidence": "high",
                  "visibilityLimits": []
                }
                """));

        var response = analyze(provider, request);

        assertEquals("copilot-sdk", response.providerName());
        assertEquals("DOWNSTREAM_TIMEOUT", response.detectedProblem());
        assertEquals("Timeout dotyka pobrania katalogu w flow zamowienia.", response.functionalAnalysis());
        assertEquals("- Sprawdz `CatalogClient` i latency downstream.", response.technicalAnalysis());
        assertEquals("Zamowienia", response.affectedProcess());
        assertEquals("Ordering", response.affectedBoundedContext());
        assertEquals("Orders Team", response.affectedTeam());
        assertEquals("high", response.confidence());
        assertEquals("Prepared JSON prompt", response.prompt());
    }

    private CopilotInitialAnalysisProvider provider(
            CopilotIncidentInitialPreparationService preparationService,
            CopilotSdkExecutionGateway executionGateway
    ) {
        return new CopilotInitialAnalysisProvider(
                preparationService,
                executionGateway,
                new CopilotResponseParser(objectMapper)
        );
    }

    private CopilotExecutionResult executionResult(String content) {
        return new CopilotExecutionResult(content, null);
    }

    private InitialAnalysisResponse analyze(CopilotInitialAnalysisProvider provider, InitialAnalysisRequest request) {
        try (var prepared = provider.prepare(request)) {
            return provider.analyze(prepared, AnalysisAiToolEvidenceListener.NO_OP);
        }
    }
}
