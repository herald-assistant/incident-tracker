package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparedRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityGate;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.response.CopilotResponseParser;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotSdkAnalysisAiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapCopilotSdkOutputToDomainResponse() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);

        var request = new AnalysisAiAnalysisRequest(
                "timeout-123",
                "dev3",
                "release/2026.04",
                "sample/runtime",
                List.of(
                        new AnalysisEvidenceSection(
                                "elasticsearch",
                                "logs",
                                List.of(new AnalysisEvidenceItem(
                                        "billing-service log entry",
                                        List.of(
                                                new AnalysisEvidenceAttribute("level", "ERROR"),
                                                new AnalysisEvidenceAttribute("message", "Read timed out while calling catalog-service")
                                        )
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "dynatrace",
                                "traces",
                                List.of(new AnalysisEvidenceItem(
                                        "catalog-service GET /inventory",
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
                                        "edge-client-service change hint",
                                        List.of(new AnalysisEvidenceAttribute(
                                                "summary",
                                                "HTTP client timeout defaults were changed."
                                        ))
                                ))
                        )
                )
        );
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparationService.prepare(request)).thenReturn(preparedRequest);
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for timeout-123");
        when(executionGateway.execute(same(preparedRequest), same(AnalysisAiToolEvidenceListener.NO_OP))).thenReturn("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "summary": "Downstream timeout is likely tied to recent HTTP client timeout changes.",
                  "recommendedAction": "Inspect dependency latency distribution and adjust timeout defaults with targeted metrics.",
                  "rationale": "Timeout signals appeared in both logs and traces and align with the recent GitLab hint.",
                  "affectedFunction": "The affected function is the outbound inventory lookup used by the billing flow before it can finalize the response.",
                  "affectedProcess": "Rozliczenie klienta",
                  "affectedBoundedContext": "Billing Context",
                  "affectedTeam": "Core Integration Team",
                  "confidence": "high",
                  "evidenceReferences": [],
                  "visibilityLimits": []
                }
                """.trim());

        var response = provider.analyze(request);

        assertEquals("copilot-sdk", response.providerName());
        assertEquals(
                "Downstream timeout is likely tied to recent HTTP client timeout changes.",
                response.summary()
        );
        assertEquals(
                "DOWNSTREAM_TIMEOUT",
                response.detectedProblem()
        );
        assertEquals(
                "Inspect dependency latency distribution and adjust timeout defaults with targeted metrics.",
                response.recommendedAction()
        );
        assertEquals(
                "Timeout signals appeared in both logs and traces and align with the recent GitLab hint.",
                response.rationale()
        );
        assertEquals(
                "The affected function is the outbound inventory lookup used by the billing flow before it can finalize the response.",
                response.affectedFunction()
        );
        assertEquals("Rozliczenie klienta", response.affectedProcess());
        assertEquals("Billing Context", response.affectedBoundedContext());
        assertEquals("Core Integration Team", response.affectedTeam());
        assertEquals("Prepared prompt for timeout-123", response.prompt());

        verify(preparationService).prepare(request);
        verify(executionGateway).execute(same(preparedRequest), same(AnalysisAiToolEvidenceListener.NO_OP));
    }

    @Test
    void shouldFallbackWhenCopilotResponseIsNotStructured() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);

        var request = new AnalysisAiAnalysisRequest("corr-123", "dev1", "main", "sample/runtime", List.of());
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparationService.prepare(request)).thenReturn(preparedRequest);
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-123");
        when(executionGateway.execute(same(preparedRequest), same(AnalysisAiToolEvidenceListener.NO_OP)))
                .thenReturn("Looks like a timeout, but the answer is not formatted.");

        var response = provider.analyze(request);

        assertEquals("copilot-sdk", response.providerName());
        assertEquals("AI_UNSTRUCTURED_RESPONSE", response.detectedProblem());
        assertEquals("Looks like a timeout, but the answer is not formatted.", response.summary());
        assertEquals(
                "Review the raw Copilot response and improve response formatting in the prompt.",
                response.recommendedAction()
        );
        assertEquals(
                "Generated by GitHub Copilot SDK from the prepared diagnostic evidence.",
                response.rationale()
        );
        assertEquals("", response.affectedFunction());
        assertEquals("", response.affectedProcess());
        assertEquals("", response.affectedBoundedContext());
        assertEquals("", response.affectedTeam());
        assertEquals("Prepared prompt for corr-123", response.prompt());
    }

    @Test
    void shouldParseMarkdownFormattedStructuredResponse() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);

        var request = new AnalysisAiAnalysisRequest("corr-456", "dev1", "dev/zephyr", "TENANT-ALPHA", List.of());
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparationService.prepare(request)).thenReturn(preparedRequest);
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-456");
        when(executionGateway.execute(same(preparedRequest), same(AnalysisAiToolEvidenceListener.NO_OP))).thenReturn("""
                {
                  "detectedProblem": "EntityNotFoundException thrown when no ActiveCaseRecord exists for caseId 7001234567 matching the active-status filter",
                  "summary": "**Żądanie** dla `CASE_ID=7001234567` dotarło do `ActiveCaseRecordController.getActiveCaseRecordForCaseId`.\\n\\n- **Potwierdzone:** wywołanie przeszło przez `ActiveCaseRecordQueryService` i zakończyło się w `ActiveCaseRecordDomainRepository`.\\n- **Granica widoczności:** brak bezpośredniego wglądu w DB i w faktyczną listę `statuses`.",
                  "recommendedAction": "- **Zespół backend:** zweryfikuj, czy `ActiveCaseRecord` dla `CASE_ID=7001234567` powinien istnieć w oczekiwanych statusach.\\n- **DBA / integracja:** potwierdź, czy rekord został zapisany przed wywołaniem API.",
                  "rationale": "- **Logi:** stacktrace i przepływ kontrolera wskazują ten sam punkt awarii.\\n- **Kod:** `orElseThrow()` w repozytorium wspiera hipotezę o braku danych, a nie o błędzie mapowania.",
                  "affectedFunction": "`ActiveCaseRecordController.getActiveCaseRecordForCaseId` obsługuje odczyt aktywnego rekordu sprawy dla klienta API.\\n\\n- Wejście przechodzi przez warstwę kontrolera i serwis zapytań.\\n- Krytyczny krok to pobranie rekordu z repozytorium domenowego po `caseId` i statusach aktywnych.\\n- Incydent przerywa flow dokładnie tam, gdzie system oczekuje istniejącego rekordu, ale go nie znajduje.",
                  "affectedProcess": "Obsługa aktywnej sprawy",
                  "affectedBoundedContext": "Case Management",
                  "affectedTeam": "Zespół Case API",
                  "confidence": "medium",
                  "evidenceReferences": [],
                  "visibilityLimits": []
                }
                """.trim());

        var response = provider.analyze(request);

        assertEquals(
                "EntityNotFoundException thrown when no ActiveCaseRecord exists for caseId 7001234567 matching the active-status filter",
                response.detectedProblem()
        );
        assertEquals(
                """
                        **Żądanie** dla `CASE_ID=7001234567` dotarło do `ActiveCaseRecordController.getActiveCaseRecordForCaseId`.

                        - **Potwierdzone:** wywołanie przeszło przez `ActiveCaseRecordQueryService` i zakończyło się w `ActiveCaseRecordDomainRepository`.
                        - **Granica widoczności:** brak bezpośredniego wglądu w DB i w faktyczną listę `statuses`.
                        """.trim(),
                response.summary()
        );
        assertEquals(
                """
                        - **Zespół backend:** zweryfikuj, czy `ActiveCaseRecord` dla `CASE_ID=7001234567` powinien istnieć w oczekiwanych statusach.
                        - **DBA / integracja:** potwierdź, czy rekord został zapisany przed wywołaniem API.
                        """.trim(),
                response.recommendedAction()
        );
        assertEquals(
                """
                        - **Logi:** stacktrace i przepływ kontrolera wskazują ten sam punkt awarii.
                        - **Kod:** `orElseThrow()` w repozytorium wspiera hipotezę o braku danych, a nie o błędzie mapowania.
                        """.trim(),
                response.rationale()
        );
        assertEquals(
                """
                        `ActiveCaseRecordController.getActiveCaseRecordForCaseId` obsługuje odczyt aktywnego rekordu sprawy dla klienta API.

                        - Wejście przechodzi przez warstwę kontrolera i serwis zapytań.
                        - Krytyczny krok to pobranie rekordu z repozytorium domenowego po `caseId` i statusach aktywnych.
                        - Incydent przerywa flow dokładnie tam, gdzie system oczekuje istniejącego rekordu, ale go nie znajduje.
                        """.trim(),
                response.affectedFunction()
        );
        assertEquals("Obsługa aktywnej sprawy", response.affectedProcess());
        assertEquals("Case Management", response.affectedBoundedContext());
        assertEquals("Zespół Case API", response.affectedTeam());
        assertEquals("Prepared prompt for corr-456", response.prompt());
    }

    @Test
    void shouldFallbackWhenStructuredResponseMissesAffectedFunction() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(preparationService, executionGateway);

        var request = new AnalysisAiAnalysisRequest("corr-999", "dev1", "main", "sample/runtime", List.of());
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparationService.prepare(request)).thenReturn(preparedRequest);
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-999");
        when(executionGateway.execute(same(preparedRequest), same(AnalysisAiToolEvidenceListener.NO_OP))).thenReturn("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "summary": "Timeout is visible in the downstream call path.",
                  "recommendedAction": "Verify timeout defaults and downstream latency.",
                  "rationale": "Logs and traces both point to the same timeout symptom.",
                  "affectedProcess": "Orders",
                  "affectedBoundedContext": "Ordering",
                  "affectedTeam": "Orders Team",
                  "confidence": "low",
                  "evidenceReferences": [],
                  "visibilityLimits": []
                }
                """.trim());

        var response = provider.analyze(request);

        assertEquals("DOWNSTREAM_TIMEOUT", response.detectedProblem());
        assertEquals("", response.affectedFunction());
        assertEquals("Orders", response.affectedProcess());
        assertEquals("Ordering", response.affectedBoundedContext());
        assertEquals("Orders Team", response.affectedTeam());
    }

    private CopilotSdkAnalysisAiProvider provider(
            CopilotSdkPreparationService preparationService,
            CopilotSdkExecutionGateway executionGateway
    ) {
        var properties = new CopilotMetricsProperties();
        var registry = new CopilotSessionMetricsRegistry(properties);
        var logger = new CopilotMetricsLogger(properties, objectMapper);
        return new CopilotSdkAnalysisAiProvider(
                preparationService,
                executionGateway,
                new CopilotResponseParser(objectMapper),
                new CopilotResponseQualityGate(new CopilotResponseQualityProperties()),
                registry,
                logger
        );
    }

}


