package pl.mkn.incidenttracker.analysis.ai.copilot.coverage;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceRuntimeEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabResolvedCodeEvidenceView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CopilotEvidenceCoverageEvaluator {

    private static final int SHORT_CODE_CONTEXT_CHARACTERS = 300;

    public CopilotEvidenceCoverageReport evaluate(AnalysisAiAnalysisRequest request) {
        var sections = request != null
                ? request.evidenceSections()
                : List.<AnalysisEvidenceSection>of();
        var gaps = new ArrayList<EvidenceGap>();
        var elasticCoverage = elasticCoverage(sections, gaps);
        var gitLabCoverage = gitLabCoverage(sections, gaps);
        var operationalContextCoverage = operationalContextCoverage(sections);
        var runtimeCoverage = runtimeCoverage(sections, elasticCoverage);
        var dataDiagnosticNeed = dataDiagnosticNeed(sections);
        var environmentResolved = request != null && StringUtils.hasText(request.environment());

        if ((dataDiagnosticNeed == DataDiagnosticNeed.LIKELY || dataDiagnosticNeed == DataDiagnosticNeed.REQUIRED)
                && !environmentResolved) {
            gaps.add(gap(
                    "DB_ENVIRONMENT_UNRESOLVED",
                    "Data diagnostics look relevant, but environment is not resolved, so DB tools must stay disabled."
            ));
        }

        if (dataDiagnosticNeed == DataDiagnosticNeed.LIKELY || dataDiagnosticNeed == DataDiagnosticNeed.REQUIRED) {
            gaps.add(gap(
                    "DB_DIAGNOSTIC_NEEDED",
                    "Evidence suggests a data-dependent symptom that may require read-only DB verification."
            ));
        }

        return new CopilotEvidenceCoverageReport(
                elasticCoverage,
                gitLabCoverage,
                runtimeCoverage,
                operationalContextCoverage,
                dataDiagnosticNeed,
                environmentResolved,
                deduplicate(gaps)
        );
    }

    private ElasticEvidenceCoverage elasticCoverage(
            List<AnalysisEvidenceSection> sections,
            List<EvidenceGap> gaps
    ) {
        var elastic = ElasticLogEvidenceView.from(sections);
        if (elastic.isEmpty()) {
            gaps.add(gap("MISSING_LOGS", "No Elasticsearch log evidence is attached for this incident."));
            return ElasticEvidenceCoverage.NONE;
        }

        var anyTruncated = false;
        var anyException = false;
        var anyStackTrace = false;
        var anyClass = false;
        var anyMessage = false;
        var anyService = false;

        for (var entry : elastic.entries()) {
            anyTruncated |= entry.messageTruncated() || entry.exceptionTruncated();
            anyException |= hasText(entry.exception()) || containsAny(normalize(entry.message()), "exception", "error");
            anyStackTrace |= looksLikeStackTrace(entry.exception()) || looksLikeStackTrace(entry.message());
            anyClass |= hasText(entry.className()) || containsAny(normalize(entry.exception()), ".java:", " at ");
            anyMessage |= hasText(entry.message());
            anyService |= hasText(entry.serviceName());
        }

        if (anyTruncated) {
            gaps.add(gap("TRUNCATED_LOGS", "At least one Elasticsearch message or exception was truncated."));
            return ElasticEvidenceCoverage.TRUNCATED;
        }
        if (anyException && !anyStackTrace) {
            gaps.add(gap("MISSING_STACKTRACE", "Logs include an exception signal, but no stacktrace frame was attached."));
            return ElasticEvidenceCoverage.EXCEPTION_PRESENT;
        }
        if (anyStackTrace && anyClass && anyMessage && anyService) {
            return ElasticEvidenceCoverage.SUFFICIENT;
        }
        if (anyStackTrace) {
            return ElasticEvidenceCoverage.STACKTRACE_PRESENT;
        }
        if (anyException) {
            return ElasticEvidenceCoverage.EXCEPTION_PRESENT;
        }

        return ElasticEvidenceCoverage.LOGS_PRESENT_NO_EXCEPTION;
    }

    private GitLabEvidenceCoverage gitLabCoverage(
            List<AnalysisEvidenceSection> sections,
            List<EvidenceGap> gaps
    ) {
        var gitLab = GitLabResolvedCodeEvidenceView.from(sections);
        if (!gitLab.hasItems()) {
            gaps.add(gap("MISSING_CODE_CONTEXT", "No deterministic GitLab code evidence is attached."));
            return GitLabEvidenceCoverage.NONE;
        }

        var itemCount = gitLab.items().size();
        var filePaths = new LinkedHashSet<String>();
        var roles = new LinkedHashSet<String>();
        var hasSymbolOrFile = false;
        var hasLine = false;
        var hasContent = false;
        var hasShortContent = false;
        var hasMethodContent = false;
        var hasDirectCollaborator = false;
        var hasFlowContextMarker = false;
        var truncated = false;

        for (var item : gitLab.items()) {
            if (hasText(item.filePath())) {
                filePaths.add(item.filePath());
                var role = inferRole(item.filePath(), item.title() + "\n" + safe(item.content()));
                roles.add(role);
            }
            hasSymbolOrFile |= hasText(item.symbol()) || hasText(item.filePath()) || hasText(item.rawReference());
            hasLine |= item.lineNumber() != null || item.returnedStartLine() != null || item.returnedEndLine() != null;
            hasContent |= hasText(item.content());
            hasShortContent |= hasText(item.content()) && item.content().length() < SHORT_CODE_CONTEXT_CHARACTERS;
            hasMethodContent |= looksLikeMethodContent(item.content());
            hasDirectCollaborator |= isDirectCollaborator(item.filePath(), item.title(), item.content());
            hasFlowContextMarker |= containsAny(
                    normalize(item.title() + "\n" + item.content()),
                    "flow context",
                    "upstream",
                    "downstream",
                    "controller",
                    "listener",
                    "handler",
                    "endpoint",
                    "orchestrator"
            );
            truncated |= item.contentTruncated();
        }

        if (truncated) {
            gaps.add(gap("TRUNCATED_CODE_CONTEXT", "Deterministic GitLab code evidence is truncated."));
        }

        if (roles.size() >= 3 || (hasMethodContent && hasFlowContextMarker && filePaths.size() >= 2)) {
            return GitLabEvidenceCoverage.SUFFICIENT;
        }
        if (hasMethodContent && (hasFlowContextMarker || roles.size() >= 2)) {
            return GitLabEvidenceCoverage.FLOW_CONTEXT_ATTACHED;
        }
        if (itemCount > 1 || hasDirectCollaborator) {
            gaps.add(gap("MISSING_FLOW_CONTEXT", "Code evidence has direct collaborators but not enough broader flow context."));
            return GitLabEvidenceCoverage.DIRECT_COLLABORATOR_ATTACHED;
        }
        if (hasContent && hasMethodContent) {
            gaps.add(gap("MISSING_FLOW_CONTEXT", "Code evidence shows the failing method but not the surrounding functional flow."));
            return GitLabEvidenceCoverage.FAILING_METHOD_ONLY;
        }
        if (hasLine && hasShortContent) {
            gaps.add(gap("MISSING_FLOW_CONTEXT", "Code evidence has only a stack-frame-sized code window."));
            return GitLabEvidenceCoverage.STACK_FRAME_ONLY;
        }
        if (hasSymbolOrFile) {
            gaps.add(gap("MISSING_FLOW_CONTEXT", "Code evidence identifies a symbol or file but does not include enough code context."));
            return GitLabEvidenceCoverage.SYMBOL_ONLY;
        }

        gaps.add(gap("MISSING_CODE_CONTEXT", "GitLab section exists but does not identify a usable symbol, file, line, or code window."));
        return GitLabEvidenceCoverage.NONE;
    }

    private RuntimeEvidenceCoverage runtimeCoverage(
            List<AnalysisEvidenceSection> sections,
            ElasticEvidenceCoverage elasticCoverage
    ) {
        var dynatrace = DynatraceRuntimeEvidenceView.from(sections);
        if (dynatrace.hasStructuredStatusSummary() && elasticCoverage == ElasticEvidenceCoverage.SUFFICIENT) {
            return RuntimeEvidenceCoverage.SUFFICIENT;
        }
        if (dynatrace.hasStructuredStatusSummary()) {
            return RuntimeEvidenceCoverage.RUNTIME_SIGNALS_PRESENT;
        }
        if (elasticCoverage != ElasticEvidenceCoverage.NONE) {
            return RuntimeEvidenceCoverage.LOGS_ONLY;
        }
        return RuntimeEvidenceCoverage.NONE;
    }

    private OperationalContextCoverage operationalContextCoverage(List<AnalysisEvidenceSection> sections) {
        for (var section : sections) {
            var provider = normalize(section.provider());
            var category = normalize(section.category());
            if (provider.contains("operational-context") || category.contains("operational-context")) {
                return section.hasItems()
                        ? OperationalContextCoverage.MATCHED
                        : OperationalContextCoverage.PARTIAL;
            }
        }
        return OperationalContextCoverage.NONE;
    }

    private DataDiagnosticNeed dataDiagnosticNeed(List<AnalysisEvidenceSection> sections) {
        var text = normalize(allEvidenceText(sections));

        if (containsAny(
                text,
                "entitynotfoundexception",
                "jpaobjectretrievalfailureexception",
                "nonuniqueresultexception",
                "incorrectresultsizedataaccessexception",
                "dataintegrityviolationexception",
                "constraintviolationexception"
        )) {
            return DataDiagnosticNeed.REQUIRED;
        }

        var repositorySymptom = containsAny(
                text,
                "repository returns empty",
                "repozytorium zwrocilo pusty",
                "orElseThrow",
                "not found by business key",
                "brak rekordu",
                "brak danych",
                "no result"
        );
        var dataClue = containsAny(
                text,
                "status",
                "state",
                "tenant",
                "active",
                "deleted",
                "validity",
                "caseid",
                "business key"
        );
        var stuckProcess = containsAny(text, "outbox", "event", "process stuck", "stuck", "utknal", "utknął");

        if (repositorySymptom && dataClue) {
            return DataDiagnosticNeed.LIKELY;
        }
        if (repositorySymptom || stuckProcess) {
            return DataDiagnosticNeed.POSSIBLE;
        }
        if (dataClue && containsAny(text, "jpa", "repository", "sql", "db", "database", "baza")) {
            return DataDiagnosticNeed.POSSIBLE;
        }

        return DataDiagnosticNeed.NONE;
    }

    private String allEvidenceText(List<AnalysisEvidenceSection> sections) {
        var text = new StringBuilder();
        for (var section : sections) {
            text.append(' ').append(section.provider()).append(' ').append(section.category());
            for (AnalysisEvidenceItem item : section.items()) {
                if (item == null) {
                    continue;
                }
                text.append(' ').append(item.title());
                for (AnalysisEvidenceAttribute attribute : item.attributes()) {
                    if (attribute != null) {
                        text.append(' ').append(attribute.name()).append(' ').append(attribute.value());
                    }
                }
            }
        }
        return text.toString();
    }

    private boolean looksLikeStackTrace(String value) {
        var normalized = safe(value);
        return normalized.contains(".java:")
                || normalized.contains("\tat ")
                || normalized.contains(" at ");
    }

    private boolean looksLikeMethodContent(String content) {
        if (!hasText(content)) {
            return false;
        }

        return content.contains("(")
                && (content.contains("{") || content.contains("->") || content.contains("return ") || content.contains("throw "));
    }

    private boolean isDirectCollaborator(String filePath, String title, String content) {
        var value = normalize(safe(filePath) + "\n" + safe(title) + "\n" + safe(content));
        return containsAny(
                value,
                "repository",
                "mapper",
                "client",
                "validator",
                "facade",
                "gateway",
                "converter",
                "adapter"
        );
    }

    private String inferRole(String filePath, String content) {
        var value = normalize(safe(filePath) + "\n" + safe(content));
        if (containsAny(value, "controller", "resource", "endpoint")) {
            return "entrypoint";
        }
        if (containsAny(value, "listener", "consumer", "handler")) {
            return "listener";
        }
        if (containsAny(value, "repository", "jparepository", "crudrepository", "dao")) {
            return "repository";
        }
        if (containsAny(value, "mapper", "converter", "assembler")) {
            return "mapper";
        }
        if (containsAny(value, "validator", "validation")) {
            return "validator";
        }
        if (containsAny(value, "client", "gateway", "webclient", "feign", "resttemplate")) {
            return "downstream-client";
        }
        if (containsAny(value, "service", "facade", "orchestrator", "processor")) {
            return "service";
        }
        if (containsAny(value, "entity", "embeddable")) {
            return "entity";
        }
        return "other";
    }

    private List<EvidenceGap> deduplicate(List<EvidenceGap> gaps) {
        var seen = new LinkedHashSet<String>();
        var deduplicated = new ArrayList<EvidenceGap>();
        for (var gap : gaps) {
            if (gap != null && seen.add(gap.code())) {
                deduplicated.add(gap);
            }
        }
        return List.copyOf(deduplicated);
    }

    private EvidenceGap gap(String code, String description) {
        return new EvidenceGap(code, description);
    }

    private boolean containsAny(String value, String... needles) {
        return containsAny(value, Set.of(needles));
    }

    private boolean containsAny(String value, Set<String> needles) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return needles.stream().anyMatch(value::contains);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
