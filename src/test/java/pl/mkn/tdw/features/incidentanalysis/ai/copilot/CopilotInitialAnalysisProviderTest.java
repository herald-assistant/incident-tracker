package pl.mkn.tdw.features.incidentanalysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotInitialAnalysisPreparation;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.response.CopilotResponseParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotInitialAnalysisProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapCopilotSdkOutputToDomainResponse() {
        var preparationService = mock(CopilotIncidentInitialPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);

        var request = new InitialAnalysisRequest(
                "timeout-123",
                "dev3",
                "release/2026.04",
                "CRM/runtime",
                List.of(
                        new AnalysisEvidenceSection(
                                "elasticsearch",
                                "logs",
                                List.of(new AnalysisEvidenceItem(
                                        "crm-billing-service log entry",
                                        List.of(
                                                new AnalysisEvidenceAttribute("level", "ERROR"),
                                                new AnalysisEvidenceAttribute("message", "Read timed out while calling crm-customer-profile-service")
                                        )
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "dynatrace",
                                "traces",
                                List.of(new AnalysisEvidenceItem(
                                        "crm-customer-profile-service GET /crm/customers",
                                        List.of(
                                                new AnalysisEvidenceAttribute("timeoutDetected", "true"),
                                                new AnalysisEvidenceAttribute("durationMs", "3500")
                                        )
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "gitlab",
                                "code-changes",
                                List.of(new AnalysisEvidenceItem(
                                        "crm-customer-client-service change hint",
                                        List.of(new AnalysisEvidenceAttribute(
                                                "summary",
                                                "HTTP client timeout defaults were changed."
                                        ))
                                ))
                        )
                )
        );
        var preparedRequest = mock(CopilotPreparedSession.class);

        when(preparationService.prepare(request)).thenReturn(preparedAnalysis(request, preparedRequest));
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for timeout-123");
        when(executionGateway.execute(same(preparedRequest))).thenReturn(executionResult("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "affectedProcess": "Rozliczenie klienta",
                  "affectedBoundedContext": "Billing Context",
                  "affectedTeam": "Core Integration Team",
                  "functionalAnalysis": "Timeout dotyka procesu rozliczenia klienta, ktory czeka na wynik lookupu customer-profile.",
                  "technicalAnalysis": "Inspect dependency latency distribution and adjust timeout defaults with targeted metrics.",
                  "confidence": "high",
                  "visibilityLimits": []
                }
                """.trim()));

        var response = analyze(provider, request);

        assertEquals("copilot-sdk", response.providerName());
        assertEquals(
                "DOWNSTREAM_TIMEOUT",
                response.detectedProblem()
        );
        assertEquals(
                "Timeout dotyka procesu rozliczenia klienta, ktory czeka na wynik lookupu customer-profile.",
                response.functionalAnalysis()
        );
        assertEquals(
                "Inspect dependency latency distribution and adjust timeout defaults with targeted metrics.",
                response.technicalAnalysis()
        );
        assertEquals("Rozliczenie klienta", response.affectedProcess());
        assertEquals("Billing Context", response.affectedBoundedContext());
        assertEquals("Core Integration Team", response.affectedTeam());
        assertEquals("high", response.confidence());
        assertEquals("Prepared prompt for timeout-123", response.prompt());

        verify(preparationService).prepare(request);
        verify(executionGateway).execute(same(preparedRequest));
    }

    @Test
    void shouldFallbackWhenCopilotResponseIsNotStructured() {
        var preparationService = mock(CopilotIncidentInitialPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);

        var request = new InitialAnalysisRequest("corr-123", "dev1", "main", "CRM/runtime", List.of());
        var preparedRequest = mock(CopilotPreparedSession.class);

        when(preparationService.prepare(request)).thenReturn(preparedAnalysis(request, preparedRequest));
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-123");
        when(executionGateway.execute(same(preparedRequest)))
                .thenReturn(executionResult("Looks like a timeout, but the answer is not formatted."));

        var response = analyze(provider, request);

        assertEquals("copilot-sdk", response.providerName());
        assertEquals("AI_UNSTRUCTURED_RESPONSE", response.detectedProblem());
        assertEquals("Looks like a timeout, but the answer is not formatted.", response.functionalAnalysis());
        assertEquals(
                "Nie udalo sie sparsowac technicznego wyniku analizy. Sprawdz surowa odpowiedz Copilota i prompt.",
                response.technicalAnalysis()
        );
        assertEquals("Nie ustalono", response.affectedProcess());
        assertEquals("Nie ustalono", response.affectedBoundedContext());
        assertEquals("Nie ustalono", response.affectedTeam());
        assertEquals("low", response.confidence());
        assertEquals("Prepared prompt for corr-123", response.prompt());
    }

    @Test
    void shouldParseMarkdownFormattedStructuredResponse() {
        var preparationService = mock(CopilotIncidentInitialPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);

        var request = new InitialAnalysisRequest("corr-456", "dev1", "dev/zephyr", "CRM", List.of());
        var preparedRequest = mock(CopilotPreparedSession.class);

        when(preparationService.prepare(request)).thenReturn(preparedAnalysis(request, preparedRequest));
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-456");
        when(executionGateway.execute(same(preparedRequest))).thenReturn(executionResult("""
                {
                  "detectedProblem": "EntityNotFoundException thrown when no ActiveCaseRecord exists for caseId 7001234567 matching the active-status filter",
                  "affectedProcess": "Obsługa aktywnej sprawy",
                  "affectedBoundedContext": "Case Management",
                  "affectedTeam": "Zespół Case API",
                  "functionalAnalysis": "**Żądanie** dla `CASE_ID=7001234567` dotarło do obsługi aktywnej sprawy.\\n\\n- **Potwierdzone:** proces oczekuje aktywnego rekordu sprawy.\\n- **Granica widoczności:** brak bezpośredniego wglądu w DB i w faktyczną listę `statuses`.",
                  "technicalAnalysis": "`ActiveCaseRecordController.getActiveCaseRecordForCaseId` obsługuje odczyt aktywnego rekordu sprawy dla klienta API.\\n\\n- Wejście przechodzi przez warstwę kontrolera i serwis zapytań.\\n- Krytyczny krok to pobranie rekordu z repozytorium domenowego po `caseId` i statusach aktywnych.",
                  "confidence": "medium",
                  "visibilityLimits": []
                }
                """.trim()));

        var response = analyze(provider, request);

        assertEquals(
                "EntityNotFoundException thrown when no ActiveCaseRecord exists for caseId 7001234567 matching the active-status filter",
                response.detectedProblem()
        );
        assertEquals(
                """
                        **Żądanie** dla `CASE_ID=7001234567` dotarło do obsługi aktywnej sprawy.

                        - **Potwierdzone:** proces oczekuje aktywnego rekordu sprawy.
                        - **Granica widoczności:** brak bezpośredniego wglądu w DB i w faktyczną listę `statuses`.
                        """.trim(),
                response.functionalAnalysis()
        );
        assertEquals(
                """
                        `ActiveCaseRecordController.getActiveCaseRecordForCaseId` obsługuje odczyt aktywnego rekordu sprawy dla klienta API.

                        - Wejście przechodzi przez warstwę kontrolera i serwis zapytań.
                        - Krytyczny krok to pobranie rekordu z repozytorium domenowego po `caseId` i statusach aktywnych.
                        """.trim(),
                response.technicalAnalysis()
        );
        assertEquals("Obsługa aktywnej sprawy", response.affectedProcess());
        assertEquals("Case Management", response.affectedBoundedContext());
        assertEquals("Zespół Case API", response.affectedTeam());
        assertEquals("Prepared prompt for corr-456", response.prompt());
    }

    @Test
    void shouldFallbackWhenStructuredResponseMissesTechnicalAnalysis() {
        var preparationService = mock(CopilotIncidentInitialPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);

        var request = new InitialAnalysisRequest("corr-999", "dev1", "main", "CRM/runtime", List.of());
        var preparedRequest = mock(CopilotPreparedSession.class);

        when(preparationService.prepare(request)).thenReturn(preparedAnalysis(request, preparedRequest));
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-999");
        when(executionGateway.execute(same(preparedRequest))).thenReturn(executionResult("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "affectedProcess": "Orders",
                  "affectedBoundedContext": "Ordering",
                  "affectedTeam": "Orders Team",
                  "functionalAnalysis": "Timeout is visible in the downstream call path.",
                  "confidence": "low",
                  "visibilityLimits": []
                }
                """.trim()));

        var response = analyze(provider, request);

        assertEquals("DOWNSTREAM_TIMEOUT", response.detectedProblem());
        assertEquals("Timeout is visible in the downstream call path.", response.functionalAnalysis());
        assertEquals(
                "Nie udalo sie sparsowac technicznego wyniku analizy. Sprawdz surowa odpowiedz Copilota i prompt.",
                response.technicalAnalysis()
        );
        assertEquals("Orders", response.affectedProcess());
        assertEquals("Ordering", response.affectedBoundedContext());
        assertEquals("Orders Team", response.affectedTeam());
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

    private CopilotInitialAnalysisPreparation preparedAnalysis(
            InitialAnalysisRequest request,
            CopilotPreparedSession preparedSession
    ) {
        return new CopilotInitialAnalysisPreparation(request, preparedSession);
    }

    private InitialAnalysisResponse analyze(CopilotInitialAnalysisProvider provider, InitialAnalysisRequest request) {
        try (var prepared = provider.prepare(request)) {
            return provider.analyze(prepared, AnalysisAiToolEvidenceListener.NO_OP);
        }
    }

}


