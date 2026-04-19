package pl.mkn.incidenttracker.analysis;

import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;

import java.util.List;
import java.util.Locale;

public final class TestAnalysisAiProvider implements AnalysisAiProvider {

    @Override
    public String preparePrompt(AnalysisAiAnalysisRequest request) {
        return syntheticPrompt(request);
    }

    @Override
    public AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request) {
        if (request.mode() == AnalysisMode.EXPLORATORY) {
            return new AnalysisAiAnalysisResponse(
                    "test-ai-provider",
                    "Exploratory rozszerza conservative o hipoteze, ze blad rozwinął się na dalszym odcinku flow między komponentami.",
                    "EXPANDED_FLOW_HYPOTHESIS",
                    "- Zweryfikuj komponent oznaczony jako miejsce bledu oraz kolejny krok w przeplywie.\n- Potwierdz hipotezy repozytoryjne wobec klas i konfiguracji integracyjnej.",
                    "- Fakty pochodza z logow i bazowego evidence.\n- Hipotezy pochodza z rozszerzonej interpretacji flow i wskazowek repozytoryjnych.",
                    AnalysisProblemNature.HYPOTHESIS,
                    AnalysisConfidence.MEDIUM,
                    syntheticPrompt(request),
                    exploratoryDiagram()
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

    private AnalysisFlowDiagram exploratoryDiagram() {
        return new AnalysisFlowDiagram(
                List.of(
                        new AnalysisFlowDiagramNode(
                                "backend",
                                "COMPONENT",
                                "backend",
                                "backend",
                                "FACT",
                                "2026-04-11T20:57:33.285Z",
                                List.of(new AnalysisFlowDiagramMetadata("class", "CustomerController")),
                                true
                        ),
                        new AnalysisFlowDiagramNode(
                                "catalog-service",
                                "COMPONENT",
                                "catalog-service",
                                "catalog-service",
                                "HYPOTHESIS",
                                "",
                                List.of(new AnalysisFlowDiagramMetadata("endpoint", "/inventory")),
                                false
                        )
                ),
                List.of(
                        new AnalysisFlowDiagramEdge(
                                "backend->catalog-service",
                                "backend",
                                "catalog-service",
                                1,
                                "HTTP",
                                "HYPOTHESIS",
                                "",
                                null,
                                "Hipoteza rozszerzajaca conservative na podstawie timeoutu i wskazowek repo."
                        )
                )
        );
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

