package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.SessionConfig;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotInitialAnalysisPreparation;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.response.CopilotResponseParser;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CopilotInitialAnalysisProviderPreparedFlowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAnalyzeAlreadyPreparedRequestWithoutPreparingAgain() {
        var preparationService = mock(CopilotIncidentInitialPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);
        var request = new InitialAnalysisRequest("corr-prepared", "zt01", "main", "CRM/runtime", List.of());
        var preparedSession = new CopilotPreparedSession(
                request.correlationId(),
                new CopilotClientOptions(),
                new SessionConfig().setSessionId("analysis-prepared"),
                new MessageOptions().setPrompt("Prepared prompt body"),
                "Prepared prompt body",
                Map.of()
        );
        var preparedAnalysis = new CopilotInitialAnalysisPreparation(request, preparedSession);

        when(executionGateway.execute(same(preparedSession)))
                .thenReturn(executionResult("""
                        {
                          "detectedProblem": "PREPARED_RESPONSE",
                          "affectedProcess": "Prepared analysis",
                          "affectedBoundedContext": "AI Runtime",
                          "affectedTeam": "AI Platform",
                          "functionalAnalysis": "Prepared request executed for the AI runtime process.",
                          "technicalAnalysis": "Keep using the prepared request.",
                          "confidence": "medium",
                          "visibilityLimits": []
                        }
                        """));

        var response = provider.analyze(preparedAnalysis, AnalysisAiToolEvidenceListener.NO_OP);

        assertEquals("PREPARED_RESPONSE", response.detectedProblem());
        assertEquals("Prepared prompt body", response.prompt());
        assertEquals("sdk-session-test", response.copilotSessionId());
        verifyNoInteractions(preparationService);
        verify(executionGateway).execute(same(preparedSession));
    }

    @Test
    void shouldClosePreparedAnalysisWhenCallerOwnsPreparation() {
        var preparationService = mock(CopilotIncidentInitialPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);
        var request = new InitialAnalysisRequest("corr-owned", "zt01", "main", "CRM/runtime", List.of());
        var preparedSession = mock(CopilotPreparedSession.class);
        var preparedAnalysis = new CopilotInitialAnalysisPreparation(request, preparedSession);

        when(preparationService.prepare(request)).thenReturn(preparedAnalysis);
        when(preparedSession.prompt()).thenReturn("Owned prompt body");
        when(executionGateway.execute(same(preparedSession)))
                .thenReturn(executionResult(structuredResponse("OWNED_RESPONSE")));

        InitialAnalysisResponse response;
        try (var prepared = provider.prepare(request)) {
            response = provider.analyze(prepared, AnalysisAiToolEvidenceListener.NO_OP);
        }

        assertEquals("OWNED_RESPONSE", response.detectedProblem());
        assertEquals("Owned prompt body", response.prompt());
        verify(executionGateway).execute(same(preparedSession));
        verify(preparedSession).close();
    }

    @Test
    void shouldNotClosePreparedRequestOwnedByCaller() {
        var preparationService = mock(CopilotIncidentInitialPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);
        var request = new InitialAnalysisRequest("corr-caller-owned", "zt01", "main", "CRM/runtime", List.of());
        var preparedSession = spy(new CopilotPreparedSession(
                request.correlationId(),
                new CopilotClientOptions(),
                new SessionConfig().setSessionId("analysis-caller-owned"),
                new MessageOptions().setPrompt("Caller-owned prompt body"),
                "Caller-owned prompt body",
                Map.of()
        ));
        var preparedAnalysis = new CopilotInitialAnalysisPreparation(request, preparedSession);
        var listener = mock(AnalysisAiToolEvidenceListener.class);

        when(executionGateway.execute(any(CopilotPreparedSession.class)))
                .thenAnswer(invocation -> {
                    var session = (CopilotPreparedSession) invocation.getArgument(0);
                    session.evidenceSink().accept(new AnalysisEvidenceSection("test", "tool-results", List.of()));
                    return executionResult(structuredResponse("CALLER_OWNED_RESPONSE"));
                });

        var response = provider.analyze(preparedAnalysis, listener);

        assertEquals("CALLER_OWNED_RESPONSE", response.detectedProblem());
        assertEquals("Caller-owned prompt body", response.prompt());
        verifyNoInteractions(preparationService);
        verify(listener).onToolEvidenceUpdated(any(AnalysisEvidenceSection.class));
        verify(executionGateway).execute(argThat(session -> session != preparedSession
                && "Caller-owned prompt body".equals(session.prompt())));
        verify(preparedSession, never()).close();
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
        return new CopilotExecutionResult(content, null, "sdk-session-test");
    }

    private String structuredResponse(String detectedProblem) {
        return """
                {
                  "detectedProblem": "%s",
                  "affectedProcess": "Prepared analysis",
                  "affectedBoundedContext": "AI Runtime",
                  "affectedTeam": "AI Platform",
                  "functionalAnalysis": "Prepared request executed for the AI runtime process.",
                  "technicalAnalysis": "Keep using the prepared request.",
                  "confidence": "medium",
                  "visibilityLimits": []
                }
                """.formatted(detectedProblem);
    }
}
