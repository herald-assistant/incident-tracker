package pl.mkn.incidenttracker.analysis;

import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;

import java.util.Locale;

public final class TestAnalysisAiProvider implements AnalysisAiProvider {

    @Override
    public String preparePrompt(AnalysisAiAnalysisRequest request) {
        return syntheticPrompt(request);
    }

    @Override
    public AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request) {
        if (request.mode() == AnalysisMode.EXPLORATORY && hasExploratoryEvidence(request)) {
            return new AnalysisAiAnalysisResponse(
                    "test-ai-provider",
                    "Eksploracyjna rekonstrukcja sugeruje, ze problem najpewniej rozwinal sie dalej poza komponentem z logiem bledu.",
                    "RECONSTRUCTED_FLOW_HYPOTHESIS",
                    "- Zweryfikuj klasy i endpointy wskazane przez exploratory flow.\n- Potwierdz hipotezy wobec brakujacych komponentow lub integracji.",
                    "- Fakty pochodza z logow i deterministycznego GitLaba.\n- Hipotezy pochodza z deep search repo i rekonstrukcji flow.",
                    AnalysisProblemNature.HYPOTHESIS,
                    AnalysisConfidence.MEDIUM,
                    syntheticPrompt(request)
            );
        }

        if (hasTimeoutEvidence(request)) {
            return new AnalysisAiAnalysisResponse(
                    "test-ai-provider",
                    "Structured evidence points to a downstream timeout in the catalog-service call chain.",
                    "DOWNSTREAM_TIMEOUT",
                    "Inspect recent HTTP client timeout changes, compare downstream latency percentiles, and add focused timeout diagnostics around the slow dependency.",
                    "The test AI provider correlated timeout signals from logs, Dynatrace runtime evidence, and recent GitLab change hints.",
                    AnalysisProblemNature.CONFIRMED,
                    AnalysisConfidence.HIGH,
                    syntheticPrompt(request)
            );
        }

        if (hasDatabaseLockEvidence(request)) {
            return new AnalysisAiAnalysisResponse(
                    "test-ai-provider",
                    "Structured evidence points to database lock contention during order write operations.",
                    "DATABASE_LOCK",
                    "Review transaction scope changes first, inspect blocked sessions, and narrow long-running write transactions in the affected persistence flow.",
                    "The test AI provider connected lock-related log and Dynatrace runtime signals with the recent GitLab persistence-layer hint.",
                    AnalysisProblemNature.CONFIRMED,
                    AnalysisConfidence.HIGH,
                    syntheticPrompt(request)
            );
        }

        return new AnalysisAiAnalysisResponse(
                "test-ai-provider",
                "Structured evidence is not yet strong enough for a confident diagnosis.",
                "UNKNOWN",
                "Collect more evidence from logs, Dynatrace runtime signals, and recent code changes before proposing a code-level fix.",
                "The test AI provider could not find a strong pattern in the available evidence.",
                AnalysisProblemNature.HYPOTHESIS,
                AnalysisConfidence.LOW,
                syntheticPrompt(request)
        );
    }

    private String syntheticPrompt(AnalysisAiAnalysisRequest request) {
        return "Synthetic AI prompt for mode=%s, correlationId=%s, environment=%s, gitLabBranch=%s"
                .formatted(request.mode(), request.correlationId(), request.environment(), request.gitLabBranch());
    }

    private boolean hasTimeoutEvidence(AnalysisAiAnalysisRequest request) {
        return request.evidenceSections().stream()
                .flatMap(section -> section.items().stream())
                .anyMatch(item -> isErrorLogMessage(item, "timed out") || hasAttributeValue(item, "timeoutDetected", "true"));
    }

    private boolean hasDatabaseLockEvidence(AnalysisAiAnalysisRequest request) {
        return request.evidenceSections().stream()
                .flatMap(section -> section.items().stream())
                .anyMatch(item -> isErrorLogMessage(item, "deadlock") || hasAttributeValue(item, "databaseLockDetected", "true"));
    }

    private boolean hasExploratoryEvidence(AnalysisAiAnalysisRequest request) {
        return request.evidenceSections().stream()
                .anyMatch(section -> "exploratory-flow".equals(section.provider())
                        && "reconstructed-flow".equals(section.category())
                        && section.hasItems());
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

}

