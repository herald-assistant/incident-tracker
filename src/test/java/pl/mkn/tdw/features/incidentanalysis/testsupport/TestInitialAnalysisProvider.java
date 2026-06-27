package pl.mkn.tdw.features.incidentanalysis.testsupport;

import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisPreparation;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisProvider;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;

import java.util.List;
import java.util.Locale;

public final class TestInitialAnalysisProvider implements InitialAnalysisProvider {

    @Override
    public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
        return new TestPreparedAnalysis(
                "test-ai-provider",
                request.correlationId(),
                syntheticPrompt(request),
                request
        );
    }

    private InitialAnalysisResponse responseFor(InitialAnalysisRequest request) {
        if (hasTimeoutEvidence(request)) {
            return new InitialAnalysisResponse(
                    "test-ai-provider",
                    "DOWNSTREAM_TIMEOUT",
                    "Billing customer-profile lookup",
                    "Billing Context",
                    "Core Integration Team",
                    "Analiza funkcjonalna: incydent dotyka procesu billingowego, ktory pobiera dane katalogowe przed zbudowaniem odpowiedzi.",
                    "Analiza techniczna: sprawdz timeout klienta katalogu, latency downstream i konfiguracje retry w sciezce outbound lookup.",
                    "medium",
                    List.of("Test provider korzysta z syntetycznych evidence."),
                    syntheticPrompt(request),
                    null,
                    syntheticSessionId(request)
            );
        }

        if (hasDatabaseLockEvidence(request)) {
            return new InitialAnalysisResponse(
                    "test-ai-provider",
                    "DATABASE_LOCK",
                    "Customer persistence update",
                    "Customer Management Context",
                    "Customer Persistence Team",
                    "Analiza funkcjonalna: incydent dotyka zapisu zamowienia po walidacji domenowej.",
                    "Analiza techniczna: sprawdz zakres transakcji, blokady sesji i ostatnie zmiany w warstwie persistence.",
                    "medium",
                    List.of("Test provider korzysta z syntetycznych evidence."),
                    syntheticPrompt(request),
                    null,
                    syntheticSessionId(request)
            );
        }

        return new InitialAnalysisResponse(
                "test-ai-provider",
                "UNKNOWN",
                "",
                "",
                "",
                "Analiza funkcjonalna: brakuje wystarczajacego wzorca w syntetycznych evidence.",
                "Analiza techniczna: zbierz dodatkowe logi, runtime signals i kontekst kodu przed wskazaniem poprawki.",
                "low",
                List.of("Brak mocnego wzorca w danych testowych."),
                syntheticPrompt(request),
                null,
                syntheticSessionId(request)
        );
    }

    @Override
    public InitialAnalysisResponse analyze(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        if (preparedAnalysis instanceof TestPreparedAnalysis testPreparedAnalysis) {
            return responseFor(testPreparedAnalysis.request());
        }

        throw new IllegalArgumentException("Unsupported prepared analysis: " + preparedAnalysis);
    }

    private String syntheticPrompt(InitialAnalysisRequest request) {
        return "Synthetic AI prompt for correlationId=%s, environment=%s, gitLabBranch=%s"
                .formatted(request.correlationId(), request.environment(), request.gitLabBranch());
    }

    private String syntheticSessionId(InitialAnalysisRequest request) {
        return "test-copilot-session-" + request.correlationId();
    }

    private boolean hasTimeoutEvidence(InitialAnalysisRequest request) {
        return request.evidenceSections().stream()
                .flatMap(section -> section.items().stream())
                .anyMatch(item -> isErrorLogMessage(item, "timed out") || hasAttributeValue(item, "timeoutDetected", "true"));
    }

    private boolean hasDatabaseLockEvidence(InitialAnalysisRequest request) {
        return request.evidenceSections().stream()
                .flatMap(section -> section.items().stream())
                .anyMatch(item -> isErrorLogMessage(item, "deadlock") || hasAttributeValue(item, "databaseLockDetected", "true"));
    }

    private boolean isErrorLogMessage(AnalysisEvidenceItem item, String phrase) {
        return hasAttributeValue(item, "level", "ERROR")
                && hasAttributeContaining(item, "message", phrase);
    }

    private boolean hasAttributeValue(AnalysisEvidenceItem item, String attributeName, String expectedValue) {
        return item.attributes().stream()
                .anyMatch(attribute -> attribute.name().equals(attributeName)
                        && attribute.value().equalsIgnoreCase(expectedValue));
    }

    private boolean hasAttributeContaining(AnalysisEvidenceItem item, String attributeName, String expectedPhrase) {
        return item.attributes().stream()
                .anyMatch(attribute -> attribute.name().equals(attributeName)
                        && attribute.value().toLowerCase(Locale.ROOT).contains(expectedPhrase));
    }

    private record TestPreparedAnalysis(
            String providerName,
            String correlationId,
            String prompt,
            InitialAnalysisRequest request
    ) implements InitialAnalysisPreparation {
    }

}
