package pl.mkn.incidenttracker.analysis.ai.copilot;

import pl.mkn.incidenttracker.analysis.AnalysisConfidence;
import pl.mkn.incidenttracker.analysis.AnalysisMode;
import pl.mkn.incidenttracker.analysis.AnalysisProblemNature;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparedRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotSdkAnalysisAiProviderTest {

    @Test
    void shouldMapCopilotSdkOutputToDomainResponse() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = new CopilotSdkAnalysisAiProvider(preparationService, executionGateway);

        var request = new AnalysisAiAnalysisRequest(
                "timeout-123",
                "dev3",
                "release/2026.04",
                "sample/runtime",
                AnalysisMode.CONSERVATIVE,
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
        when(executionGateway.execute(preparedRequest)).thenReturn("""
                detectedProblem: DOWNSTREAM_TIMEOUT
                summary: Downstream timeout is likely tied to recent HTTP client timeout changes.
                recommendedAction: Inspect dependency latency distribution and adjust timeout defaults with targeted metrics.
                rationale: Timeout signals appeared in both logs and traces and align with the recent GitLab hint.
                problemNature: CONFIRMED
                confidence: HIGH
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
        assertEquals(AnalysisProblemNature.CONFIRMED, response.problemNature());
        assertEquals(AnalysisConfidence.HIGH, response.confidence());
        assertEquals("Prepared prompt for timeout-123", response.prompt());

        verify(preparationService).prepare(request);
        verify(executionGateway).execute(same(preparedRequest));
    }

    @Test
    void shouldFallbackWhenCopilotResponseIsNotStructured() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = new CopilotSdkAnalysisAiProvider(preparationService, executionGateway);

        var request = new AnalysisAiAnalysisRequest("corr-123", "dev1", "main", "sample/runtime", AnalysisMode.CONSERVATIVE, List.of());
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparationService.prepare(request)).thenReturn(preparedRequest);
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-123");
        when(executionGateway.execute(preparedRequest)).thenReturn("Looks like a timeout, but the answer is not formatted.");

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
        assertEquals(AnalysisProblemNature.CONFIRMED, response.problemNature());
        assertEquals(AnalysisConfidence.HIGH, response.confidence());
        assertEquals("Prepared prompt for corr-123", response.prompt());
    }

    @Test
    void shouldParseMarkdownFormattedStructuredResponse() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = new CopilotSdkAnalysisAiProvider(preparationService, executionGateway);

        var request = new AnalysisAiAnalysisRequest("corr-456", "dev1", "dev/zephyr", "TENANT-ALPHA", AnalysisMode.CONSERVATIVE, List.of());
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparationService.prepare(request)).thenReturn(preparedRequest);
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-456");
        when(executionGateway.execute(preparedRequest)).thenReturn("""
                The evidence is already strong and no more fetches are needed.

                **detectedProblem:** EntityNotFoundException thrown when no ActiveCaseRecord exists for caseId 7001234567 matching the active-status filter
                **summary:** **Żądanie** dla `CASE_ID=7001234567` dotarło do `ActiveCaseRecordController.getActiveCaseRecordForCaseId`.

                - **Potwierdzone:** wywołanie przeszło przez `ActiveCaseRecordQueryService` i zakończyło się w `ActiveCaseRecordDomainRepository`.
                - **Granica widoczności:** brak bezpośredniego wglądu w DB i w faktyczną listę `statuses`.
                **recommendedAction:**
                - **Zespół backend:** zweryfikuj, czy `ActiveCaseRecord` dla `CASE_ID=7001234567` powinien istnieć w oczekiwanych statusach.
                - **DBA / integracja:** potwierdź, czy rekord został zapisany przed wywołaniem API.
                **rationale:**
                - **Logi:** stacktrace i przepływ kontrolera wskazują ten sam punkt awarii.
                - **Kod:** `orElseThrow()` w repozytorium wspiera hipotezę o braku danych, a nie o błędzie mapowania.
                **problemNature:** CONFIRMED
                **confidence:** MEDIUM
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
        assertEquals(AnalysisProblemNature.CONFIRMED, response.problemNature());
        assertEquals(AnalysisConfidence.MEDIUM, response.confidence());
        assertEquals("Prepared prompt for corr-456", response.prompt());
    }

    @Test
    void shouldNormalizeLegacyPipeSeparatedStructuredResponse() {
        var preparationService = mock(CopilotSdkPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = new CopilotSdkAnalysisAiProvider(preparationService, executionGateway);

        var request = new AnalysisAiAnalysisRequest("corr-789", "uat2", "release-candidate", "TENANT-ALPHA", AnalysisMode.CONSERVATIVE, List.of());
        var preparedRequest = mock(CopilotSdkPreparedRequest.class);

        when(preparationService.prepare(request)).thenReturn(preparedRequest);
        when(preparedRequest.prompt()).thenReturn("Prepared prompt for corr-789");
        when(executionGateway.execute(preparedRequest)).thenReturn("""
                detectedProblem: EntityNotFoundException in getLatestActiveCaseRecordByCaseIdAndStatuses
                summary: W naszym systemie widać błąd domenowy dla `CASE_ID=7007654321`. | - Najmocniej wspiera to stacktrace do `ActiveCaseRecordDomainRepository.java:74`. | - Brakuje bezpośredniego wglądu w DB i faktyczną listę `statuses`.
                recommendedAction: (Zespół backend) sprawdź rekord `ActiveCaseRecord` dla `CASE_ID=7007654321`. | - (Integracja / async) potwierdź, czy proces zasilania utworzył rekord przed wywołaniem API.
                rationale: Potwierdzone w logach: `EntityNotFoundException` i pełny stacktrace. | - Potwierdzone w kodzie: `orElseThrow()` oznacza brak wyników z warstwy danych.
                problemNature: CONFIRMED
                confidence: HIGH
                """.trim());

        var response = provider.analyze(request);

        assertEquals(
                """
                        W naszym systemie widać błąd domenowy dla `CASE_ID=7007654321`.
                        - Najmocniej wspiera to stacktrace do `ActiveCaseRecordDomainRepository.java:74`.
                        - Brakuje bezpośredniego wglądu w DB i faktyczną listę `statuses`.
                        """.trim(),
                response.summary()
        );
        assertEquals(
                """
                        - (Zespół backend) sprawdź rekord `ActiveCaseRecord` dla `CASE_ID=7007654321`.
                        - (Integracja / async) potwierdź, czy proces zasilania utworzył rekord przed wywołaniem API.
                        """.trim(),
                response.recommendedAction()
        );
        assertEquals(
                """
                        - Potwierdzone w logach: `EntityNotFoundException` i pełny stacktrace.
                        - Potwierdzone w kodzie: `orElseThrow()` oznacza brak wyników z warstwy danych.
                        """.trim(),
                response.rationale()
        );
        assertEquals(AnalysisProblemNature.CONFIRMED, response.problemNature());
        assertEquals(AnalysisConfidence.HIGH, response.confidence());
    }

}


