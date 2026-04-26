package pl.mkn.incidenttracker.analysis.ai.copilot.quality;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.response.CopilotStructuredAnalysisResponse;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CopilotResponseQualityGate {

    private static final Pattern EXCEPTION_ONLY = Pattern.compile("^[\\w.$]+Exception:?\\s*$");
    private static final List<String> FLOW_TERMS = List.of(
            "flow",
            "proces",
            "przeplyw",
            "krok",
            "wywol",
            "endpoint",
            "controller",
            "service",
            "client",
            "warstwa",
            "deleguje",
            "pobran",
            "odczyt",
            "zapis",
            "obsl",
            "przechodzi",
            "integrac"
    );
    private static final List<String> GENERIC_ACTION_PATTERNS = List.of(
            "sprawdzic logi",
            "sprawdz logi",
            "zweryfikowac aplikacje",
            "zweryfikuj aplikacje",
            "przeanalizowac baze",
            "przeanalizuj baze",
            "skontaktowac sie z zespolem",
            "skontaktuj sie z zespolem",
            "check logs",
            "verify application",
            "analyze database",
            "contact the team"
    );
    private static final List<String> DATA_ISSUE_TERMS = List.of(
            "data",
            "database",
            "db",
            "sql",
            "jpa",
            "repository",
            "entitynotfound",
            "not_found",
            "no result",
            "empty",
            "brak danych",
            "brak rekordu",
            "rekord",
            "baza"
    );

    private final CopilotResponseQualityProperties properties;

    public CopilotResponseQualityReport evaluate(
            AnalysisAiAnalysisRequest request,
            CopilotStructuredAnalysisResponse response
    ) {
        if (properties == null || !properties.isEnabled()) {
            return CopilotResponseQualityReport.disabled(mode());
        }

        var evidence = EvidenceSummary.from(request);
        var findings = new ArrayList<CopilotResponseQualityFinding>();

        validateAffectedFunction(response, findings);
        validateRecommendedAction(response, findings);
        validateDataIssueGrounding(response, evidence, findings);
        validateOwnershipGrounding(response, evidence, findings);
        validateProcessContextGrounding(response, evidence, findings);
        validateHighConfidence(response, evidence, findings);
        validateRationaleStructure(response, findings);

        return new CopilotResponseQualityReport(true, mode(), findings.isEmpty(), findings);
    }

    private void validateAffectedFunction(
            CopilotStructuredAnalysisResponse response,
            List<CopilotResponseQualityFinding> findings
    ) {
        var value = text(response != null ? response.affectedFunction() : null);
        var normalized = normalize(value);
        var hasFlowDescription = containsAny(normalized, FLOW_TERMS);
        var generic = normalized.equals("repository")
                || normalized.equals("repozytorium")
                || normalized.equals("blad bazy")
                || normalized.equals("problem w kodzie")
                || normalized.equals("blad w kodzie")
                || normalized.equals("database error");

        if (!StringUtils.hasText(value)
                || EXCEPTION_ONLY.matcher(value.trim()).matches()
                || generic
                || (value.length() < properties.getMinAffectedFunctionCharacters() && !hasFlowDescription)) {
            findings.add(finding(
                    "SHALLOW_AFFECTED_FUNCTION",
                    "affectedFunction",
                    "affectedFunction is too shallow to explain where the incident interrupts the functional flow."
            ));
        }
    }

    private void validateRecommendedAction(
            CopilotStructuredAnalysisResponse response,
            List<CopilotResponseQualityFinding> findings
    ) {
        var action = text(response != null ? response.recommendedAction() : null);
        var normalized = normalize(action);

        if (containsAny(normalized, GENERIC_ACTION_PATTERNS) && !hasConcreteActionObject(action)) {
            findings.add(finding(
                    "GENERIC_RECOMMENDED_ACTION",
                    "recommendedAction",
                    "recommendedAction is generic and does not name a concrete object, predicate, endpoint, table, class, owner, or metric to verify."
            ));
        }
    }

    private void validateDataIssueGrounding(
            CopilotStructuredAnalysisResponse response,
            EvidenceSummary evidence,
            List<CopilotResponseQualityFinding> findings
    ) {
        if (!looksLikeDataIssue(response) || evidence.databaseEvidence() || hasDbVisibilityLimit(response)) {
            return;
        }

        findings.add(finding(
                "DATA_ISSUE_WITHOUT_DB_GROUNDING",
                "detectedProblem",
                "detectedProblem suggests a data issue, but no DB evidence or explicit DB visibility limit is present."
        ));
    }

    private void validateOwnershipGrounding(
            CopilotStructuredAnalysisResponse response,
            EvidenceSummary evidence,
            List<CopilotResponseQualityFinding> findings
    ) {
        if (!isEstablished(response != null ? response.affectedTeam() : null)) {
            return;
        }

        if (!evidence.ownershipEvidence()) {
            findings.add(finding(
                    "OWNERSHIP_WITHOUT_GROUNDING",
                    "affectedTeam",
                    "affectedTeam is set, but no operational-context or ownership evidence grounds the handoff."
            ));
        }
    }

    private void validateProcessContextGrounding(
            CopilotStructuredAnalysisResponse response,
            EvidenceSummary evidence,
            List<CopilotResponseQualityFinding> findings
    ) {
        if (!isEstablished(response != null ? response.affectedProcess() : null)
                && !isEstablished(response != null ? response.affectedBoundedContext() : null)) {
            return;
        }

        if (!evidence.processContextEvidence()) {
            findings.add(finding(
                    "PROCESS_CONTEXT_WITHOUT_GROUNDING",
                    "affectedProcess",
                    "affectedProcess or affectedBoundedContext is set without operational, deployment, code, or runtime context evidence."
            ));
        }
    }

    private void validateHighConfidence(
            CopilotStructuredAnalysisResponse response,
            EvidenceSummary evidence,
            List<CopilotResponseQualityFinding> findings
    ) {
        if (!"high".equalsIgnoreCase(text(response != null ? response.confidence() : null))) {
            return;
        }

        var weakReasons = new ArrayList<String>();
        if (!evidence.codeEvidence()) {
            weakReasons.add("missing code evidence");
        }
        if (!evidence.runtimeSignal()) {
            weakReasons.add("missing runtime signal");
        }
        if (looksLikeDataIssue(response) && !evidence.databaseEvidence()) {
            weakReasons.add("missing DB confirmation for data issue");
        }
        if (response != null
                && response.visibilityLimits().size() >= properties.getHighConfidenceVisibilityLimitThreshold()) {
            weakReasons.add("too many visibility limits");
        }

        if (!weakReasons.isEmpty()) {
            findings.add(finding(
                    "HIGH_CONFIDENCE_WITH_WEAK_EVIDENCE",
                    "confidence",
                    "confidence=high is inconsistent with evidence coverage: " + String.join(", ", weakReasons) + "."
            ));
        }
    }

    private void validateRationaleStructure(
            CopilotStructuredAnalysisResponse response,
            List<CopilotResponseQualityFinding> findings
    ) {
        var rationale = normalize(response != null ? response.rationale() : null);
        var hasConfirmed = containsAny(rationale, List.of("potwierdz", "confirmed", "evidence", "dowod", "fakt", "log"));
        var hasHypothesis = containsAny(rationale, List.of("hipotez", "hypothesis", "prawdopodob", "likely", "mozliw"));
        var hasLimits = containsAny(rationale, List.of("limit", "visibility", "granica", "brak", "niezweryfik", "unverified"));

        if (!hasConfirmed || !hasHypothesis || !hasLimits) {
            findings.add(finding(
                    "RATIONALE_WITHOUT_SEPARATED_REASONING",
                    "rationale",
                    "rationale should separate confirmed evidence, best-supported hypothesis, and visibility limits."
            ));
        }
    }

    private boolean looksLikeDataIssue(CopilotStructuredAnalysisResponse response) {
        return response != null && containsAny(normalize(response.detectedProblem()), DATA_ISSUE_TERMS);
    }

    private boolean hasDbVisibilityLimit(CopilotStructuredAnalysisResponse response) {
        if (response == null) {
            return false;
        }

        return response.visibilityLimits().stream()
                .map(this::normalize)
                .anyMatch(limit -> containsAny(limit, List.of("db", "baza", "database", "dane"))
                        && containsAny(limit, List.of("niezweryfik", "nieweryfik", "unverified", "brak weryfikacji", "not verified")));
    }

    private boolean hasConcreteActionObject(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }

        return value.contains("`")
                || value.matches(".*[A-Z][A-Za-z0-9]+(?:\\.[A-Z_a-z0-9]+)+.*")
                || value.matches(".*\\b[A-Z_]{3,}\\b.*")
                || value.matches(".*\\b\\w+\\.\\w+\\b.*")
                || value.matches(".*\\b\\w+/\\w+\\b.*")
                || value.matches(".*\\b(caseId|correlationId|table|endpoint|metric|predicate|kolumn|tabel|rekord)\\b.*");
    }

    private boolean isEstablished(String value) {
        var normalized = normalize(value);
        return StringUtils.hasText(value)
                && !normalized.equals("nieustalone")
                && !normalized.equals("unknown")
                && !normalized.equals("n/a")
                && !normalized.equals("none");
    }

    private boolean containsAny(String value, List<String> candidates) {
        if (!StringUtils.hasText(value)) {
            return false;
        }

        return candidates.stream().anyMatch(value::contains);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        var decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT).trim();
    }

    private String text(String value) {
        return value != null ? value.trim() : "";
    }

    private CopilotResponseQualityProperties.Mode mode() {
        return properties != null && properties.getMode() != null
                ? properties.getMode()
                : CopilotResponseQualityProperties.Mode.REPORT_ONLY;
    }

    private CopilotResponseQualityFinding finding(String code, String field, String message) {
        return new CopilotResponseQualityFinding(
                CopilotResponseQualitySeverity.WARNING,
                code,
                field,
                message
        );
    }

    private record EvidenceSummary(
            boolean databaseEvidence,
            boolean ownershipEvidence,
            boolean processContextEvidence,
            boolean codeEvidence,
            boolean runtimeSignal
    ) {

        private static EvidenceSummary from(AnalysisAiAnalysisRequest request) {
            var sections = request != null && request.evidenceSections() != null
                    ? request.evidenceSections()
                    : List.<AnalysisEvidenceSection>of();

            var databaseEvidence = false;
            var ownershipEvidence = false;
            var processContextEvidence = false;
            var codeEvidence = false;
            var runtimeSignal = false;

            for (var section : sections) {
                var sectionText = normalizeSection(section);
                var provider = normalizeStatic(section != null ? section.provider() : null);
                var category = normalizeStatic(section != null ? section.category() : null);
                var descriptor = provider + " " + category + " " + sectionText;

                databaseEvidence |= provider.contains("database")
                        || provider.equals("db")
                        || category.contains("database")
                        || category.contains("db")
                        || descriptor.contains("db_execute")
                        || descriptor.contains("readonly_sql");
                ownershipEvidence |= provider.contains("operational-context")
                        || category.contains("operational-context")
                        || category.contains("matched-context")
                        || containsAnyStatic(descriptor, List.of("owner", "ownership", "team", "zespol", "handoff"));
                processContextEvidence |= ownershipEvidence
                        || provider.contains("dynatrace")
                        || provider.contains("gitlab")
                        || category.contains("deployment")
                        || category.contains("resolved-code")
                        || category.contains("runtime-signals")
                        || category.contains("traces")
                        || containsAnyStatic(descriptor, List.of("boundedcontext", "process", "service", "operationname", "endpoint"));
                codeEvidence |= provider.contains("gitlab")
                        || category.contains("resolved-code")
                        || category.contains("tool-fetched-code")
                        || containsAnyStatic(descriptor, List.of("filepath", ".java", "classname", "method", "repository"));
                runtimeSignal |= provider.contains("elasticsearch")
                        || provider.contains("dynatrace")
                        || category.contains("logs")
                        || category.contains("traces")
                        || category.contains("runtime-signals")
                        || containsAnyStatic(descriptor, List.of("stacktrace", "exception", "duration", "timeout", "latency"));
            }

            return new EvidenceSummary(
                    databaseEvidence,
                    ownershipEvidence,
                    processContextEvidence,
                    codeEvidence,
                    runtimeSignal
            );
        }

        private static String normalizeSection(AnalysisEvidenceSection section) {
            if (section == null || section.items() == null) {
                return "";
            }

            var text = new StringBuilder();
            for (AnalysisEvidenceItem item : section.items()) {
                if (item == null) {
                    continue;
                }
                text.append(' ').append(item.title());
                if (item.attributes() == null) {
                    continue;
                }
                for (AnalysisEvidenceAttribute attribute : item.attributes()) {
                    if (attribute != null) {
                        text.append(' ').append(attribute.name()).append(' ').append(attribute.value());
                    }
                }
            }
            return normalizeStatic(text.toString());
        }

        private static String normalizeStatic(String value) {
            if (!StringUtils.hasText(value)) {
                return "";
            }

            return Normalizer.normalize(value, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT)
                    .trim();
        }

        private static boolean containsAnyStatic(String value, List<String> candidates) {
            if (!StringUtils.hasText(value)) {
                return false;
            }

            return candidates.stream().anyMatch(value::contains);
        }
    }
}
