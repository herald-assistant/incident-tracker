package pl.mkn.incidenttracker.analysis.ai.copilot.quality;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotQualityDtos.Report;
import pl.mkn.incidenttracker.analysis.ai.copilot.response.CopilotResponseDtos.StructuredAnalysisResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotResponseQualityGateTest {

    private final CopilotResponseQualityGate gate = new CopilotResponseQualityGate(
            new CopilotResponseQualityProperties()
    );

    @Test
    void shouldFlagShallowAffectedFunction() {
        var report = gate.evaluate(runtimeAndCodeRequest(), response(
                "DOWNSTREAM_TIMEOUT",
                "- Zweryfikuj endpoint `GET /inventory` i metryke `catalog.latency.p95`.",
                "Repository",
                "nieustalone",
                "nieustalone",
                "nieustalone",
                "medium",
                List.of("Brak DB, ale problem nie wyglada na data issue.")
        ));

        assertFinding(report, "SHALLOW_AFFECTED_FUNCTION");
    }

    @Test
    void shouldFlagGenericRecommendedAction() {
        var report = gate.evaluate(runtimeAndCodeRequest(), response(
                "DOWNSTREAM_TIMEOUT",
                "Sprawdzic logi i zweryfikowac aplikacje.",
                validAffectedFunction(),
                "nieustalone",
                "nieustalone",
                "nieustalone",
                "medium",
                List.of("Brak danych o downstream latency.")
        ));

        assertFinding(report, "GENERIC_RECOMMENDED_ACTION");
    }

    @Test
    void shouldFlagOwnershipHallucination() {
        var report = gate.evaluate(
                request(List.of(section("elasticsearch", "logs", "Timeout stacktrace"))),
                response(
                        "DOWNSTREAM_TIMEOUT",
                        "- Zweryfikuj endpoint `GET /inventory`.",
                        validAffectedFunction(),
                        "nieustalone",
                        "nieustalone",
                        "Payments Team",
                        "medium",
                        List.of("Brak operational-context evidence.")
                )
        );

        assertFinding(report, "OWNERSHIP_WITHOUT_GROUNDING");
    }

    @Test
    void shouldFlagDataIssueWithoutDbVerification() {
        var report = gate.evaluate(runtimeAndCodeRequest(), response(
                "EntityNotFoundException in ActiveCaseRepository",
                "- Zweryfikuj rekord `ActiveCaseRecord` dla `caseId=7001234567`.",
                validAffectedFunction(),
                "nieustalone",
                "nieustalone",
                "nieustalone",
                "medium",
                List.of("Brak metryk runtime dla procesu asynchronicznego.")
        ));

        assertFinding(report, "DATA_ISSUE_WITHOUT_DB_GROUNDING");
    }

    @Test
    void shouldFlagHighConfidenceWithWeakEvidence() {
        var report = gate.evaluate(
                request(List.of(section("elasticsearch", "logs", "Timeout stacktrace"))),
                response(
                        "DOWNSTREAM_TIMEOUT",
                        "- Zweryfikuj endpoint `GET /inventory`.",
                        validAffectedFunction(),
                        "nieustalone",
                        "nieustalone",
                        "nieustalone",
                        "high",
                        List.of("Brak kodu.", "Brak metryk downstream.")
                )
        );

        assertFinding(report, "HIGH_CONFIDENCE_WITH_WEAK_EVIDENCE");
    }

    @Test
    void shouldFlagRationaleWithoutSeparatedReasoning() {
        var report = gate.evaluate(runtimeAndCodeRequest(), response(
                "DOWNSTREAM_TIMEOUT",
                "- Zweryfikuj endpoint `GET /inventory`.",
                validAffectedFunction(),
                "nieustalone",
                "nieustalone",
                "nieustalone",
                "medium",
                List.of("Brak metryk downstream."),
                "Problem jest w downstream i trzeba to poprawic."
        ));

        assertFinding(report, "RATIONALE_WITHOUT_SEPARATED_REASONING");
    }

    @Test
    void shouldPassUsefulGroundedResponse() {
        var report = gate.evaluate(
                request(List.of(
                        section("operational-context", "matched-context", "team=Orders Team process=Obsluga zamowienia boundedContext=Ordering"),
                        section("gitlab", "resolved-code", "filePath=src/main/java/pl/mkn/orders/OrderController.java method=getOrder"),
                        section("elasticsearch", "logs", "SocketTimeoutException calling catalog-service duration=3500")
                )),
                response(
                        "DOWNSTREAM_TIMEOUT",
                        "- Zespol Orders: zweryfikuj endpoint `GET /inventory` i metryke `catalog.latency.p95`.",
                        validAffectedFunction(),
                        "Obsluga zamowienia",
                        "Ordering",
                        "Orders Team",
                        "high",
                        List.of("Brak bezposredniego potwierdzenia konfiguracji timeoutu w downstream.")
                )
        );

        assertTrue(report.passed());
        assertTrue(report.findings().isEmpty());
    }

    private void assertFinding(Report report, String code) {
        assertFalse(report.passed());
        assertTrue(report.findings().stream().anyMatch(finding -> code.equals(finding.code())));
    }

    private StructuredAnalysisResponse response(
            String detectedProblem,
            String recommendedAction,
            String affectedFunction,
            String affectedProcess,
            String affectedBoundedContext,
            String affectedTeam,
            String confidence,
            List<String> visibilityLimits
    ) {
        return response(
                detectedProblem,
                recommendedAction,
                affectedFunction,
                affectedProcess,
                affectedBoundedContext,
                affectedTeam,
                confidence,
                visibilityLimits,
                """
                        - Potwierdzone evidence: logi pokazuja timeout w wywolaniu downstream.
                        - Hipoteza: problem prawdopodobnie dotyczy latency `catalog-service`.
                        - Granica visibility: brak bezposredniej metryki konfiguracji timeoutu.
                        """.trim()
        );
    }

    private StructuredAnalysisResponse response(
            String detectedProblem,
            String recommendedAction,
            String affectedFunction,
            String affectedProcess,
            String affectedBoundedContext,
            String affectedTeam,
            String confidence,
            List<String> visibilityLimits,
            String rationale
    ) {
        return new StructuredAnalysisResponse(
                detectedProblem,
                "Timeout jest widoczny w logach i dotyczy wywolania downstream.",
                recommendedAction,
                rationale,
                affectedFunction,
                affectedProcess,
                affectedBoundedContext,
                affectedTeam,
                confidence,
                List.of(),
                visibilityLimits
        );
    }

    private String validAffectedFunction() {
        return "Flow obslugi zamowienia przechodzi przez `OrderController`, warstwe serwisu i klienta katalogu, a incydent przerywa krok pobrania danych produktu z downstream.";
    }

    private AnalysisAiAnalysisRequest runtimeAndCodeRequest() {
        return request(List.of(
                section("gitlab", "resolved-code", "filePath=src/main/java/pl/mkn/orders/OrderController.java method=getOrder"),
                section("elasticsearch", "logs", "SocketTimeoutException calling catalog-service")
        ));
    }

    private AnalysisAiAnalysisRequest request(List<AnalysisEvidenceSection> sections) {
        return new AnalysisAiAnalysisRequest(
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime",
                sections
        );
    }

    private AnalysisEvidenceSection section(String provider, String category, String value) {
        return new AnalysisEvidenceSection(
                provider,
                category,
                List.of(new AnalysisEvidenceItem(
                        provider + " " + category,
                        List.of(new AnalysisEvidenceAttribute("details", value))
                ))
        );
    }
}
