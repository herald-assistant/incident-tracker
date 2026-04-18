package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;
import pl.mkn.incidenttracker.analysis.evidence.view.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.view.ElasticLogEvidenceView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
@Order(20)
@RequiredArgsConstructor
public class DynatraceEvidenceProvider implements AnalysisEvidenceProvider {

    private static final int MAX_PROBLEM_EVIDENCE_SUMMARY_CHARACTERS = 800;
    private static final String DATABASE_CONNECTIVITY_CATEGORY = "database-connectivity";
    private static final String AVAILABILITY_CATEGORY = "availability";
    private static final String MESSAGING_CATEGORY = "messaging";
    private static final String LATENCY_CATEGORY = "latency";
    private static final String FAILURE_RATE_CATEGORY = "failure-rate";

    private final DynatraceIncidentPort dynatraceIncidentPort;
    private final DeploymentContextResolver deploymentContextResolver;

    @Override
    public AnalysisEvidenceSection collect(AnalysisContext context) {
        var logEvidence = ElasticLogEvidenceView.from(context);
        if (logEvidence.isEmpty()) {
            return emptySection();
        }

        if (shouldSkipForDevEnvironment(
                DeploymentContextEvidenceView.from(context),
                logEvidence
        )) {
            log.info("Dynatrace enrichment skipped for dev environment correlationId={}", context.correlationId());
            return emptySection();
        }

        var query = buildQuery(context.correlationId(), logEvidence);
        if (query == null) {
            return emptySection();
        }

        try {
            var evidence = dynatraceIncidentPort.loadIncidentEvidence(query);
            if (evidence == null || !evidence.hasAnyData()) {
                return emptySection();
            }

            var items = new ArrayList<AnalysisEvidenceItem>();
            items.addAll(serviceMatchItems(query, evidence.serviceMatches()));
            items.addAll(problemItems(evidence.problems()));
            items.addAll(metricItems(evidence.metrics()));
            return new AnalysisEvidenceSection(
                    producedEvidence().provider(),
                    producedEvidence().category(),
                    List.copyOf(items)
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Dynatrace enrichment skipped correlationId={} reason={}",
                    context.correlationId(),
                    exception.getMessage()
            );
            log.debug("Dynatrace enrichment failure details correlationId={}", context.correlationId(), exception);
            return emptySection();
        }
    }

    @Override
    public AnalysisEvidenceReference producedEvidence() {
        return new AnalysisEvidenceReference("dynatrace", "runtime-signals");
    }

    @Override
    public List<AnalysisEvidenceReference> consumedEvidence() {
        return List.of(
                ElasticLogEvidenceView.EVIDENCE_REFERENCE,
                DeploymentContextEvidenceView.EVIDENCE_REFERENCE
        );
    }

    @Override
    public AnalysisStepPhase stepPhase() {
        return AnalysisStepPhase.ENRICHMENT;
    }

    @Override
    public String stepCode() {
        return "DYNATRACE_RUNTIME_SIGNALS";
    }

    @Override
    public String stepLabel() {
        return "Zbieranie danych runtime z Dynatrace";
    }

    private AnalysisEvidenceSection emptySection() {
        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.of()
        );
    }

    private boolean shouldSkipForDevEnvironment(
            DeploymentContextEvidenceView deploymentContext,
            ElasticLogEvidenceView logEvidence
    ) {
        if (!deploymentContext.isEmpty()) {
            return deploymentContext.deployments().stream()
                    .map(pl.mkn.incidenttracker.analysis.deployment.ResolvedDeploymentContext::environment)
                    .filter(StringUtils::hasText)
                    .anyMatch(environment -> environment.startsWith("dev"));
        }

        return logEvidence.entries().stream()
                .map(deploymentContextResolver::resolve)
                .filter(java.util.Objects::nonNull)
                .map(pl.mkn.incidenttracker.analysis.deployment.ResolvedDeploymentContext::environment)
                .filter(StringUtils::hasText)
                .anyMatch(environment -> environment.startsWith("dev"));
    }

    private DynatraceIncidentQuery buildQuery(String correlationId, ElasticLogEvidenceView logEvidence) {
        Instant incidentStart = null;
        Instant incidentEnd = null;
        var namespaces = new LinkedHashSet<String>();
        var podNames = new LinkedHashSet<String>();
        var containerNames = new LinkedHashSet<String>();
        var serviceNames = new LinkedHashSet<String>();

        for (var entry : logEvidence.entries()) {
            addValue(namespaces, entry.namespace());
            addValue(podNames, entry.podName());
            addValue(containerNames, entry.containerName());
            addValue(serviceNames, entry.serviceName());

            var parsedTimestamp = parseInstant(entry.timestamp());
            if (parsedTimestamp != null) {
                incidentStart = incidentStart == null || parsedTimestamp.isBefore(incidentStart)
                        ? parsedTimestamp
                        : incidentStart;
                incidentEnd = incidentEnd == null || parsedTimestamp.isAfter(incidentEnd)
                        ? parsedTimestamp
                        : incidentEnd;
            }
        }

        if (incidentStart == null || incidentEnd == null) {
            return null;
        }

        var query = new DynatraceIncidentQuery(
                correlationId,
                incidentStart,
                incidentEnd,
                List.copyOf(namespaces),
                List.copyOf(podNames),
                List.copyOf(containerNames),
                List.copyOf(serviceNames)
        );

        return query.hasLookupSignals() ? query : null;
    }

    private List<AnalysisEvidenceItem> serviceMatchItems(
            DynatraceIncidentQuery query,
            List<DynatraceIncidentEvidence.ServiceMatch> matches
    ) {
        var items = new ArrayList<AnalysisEvidenceItem>();

        for (var match : matches) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, "entityId", match.entityId());
            addAttribute(attributes, "displayName", match.displayName());
            addAttribute(attributes, "matchScore", String.valueOf(match.matchScore()));
            addAttribute(attributes, "incidentStart", renderInstant(query.incidentStart()));
            addAttribute(attributes, "incidentEnd", renderInstant(query.incidentEnd()));
            addAttribute(attributes, "matchedNamespaces", joined(match.matchedNamespaces()));
            addAttribute(attributes, "matchedPods", joined(match.matchedPods()));
            addAttribute(attributes, "matchedContainers", joined(match.matchedContainers()));
            addAttribute(attributes, "matchedServiceNames", joined(match.matchedServiceNames()));

            items.add(new AnalysisEvidenceItem(
                    "Dynatrace matched service " + shortLabel(match.displayName()),
                    List.copyOf(attributes)
            ));
        }

        return List.copyOf(items);
    }

    private List<AnalysisEvidenceItem> problemItems(List<DynatraceIncidentEvidence.ProblemSummary> problems) {
        var items = new ArrayList<AnalysisEvidenceItem>();

        for (var problem : problems) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, "problemId", problem.problemId());
            addAttribute(attributes, "displayId", problem.displayId());
            addAttribute(attributes, "title", problem.title());
            addAttribute(attributes, "severityLevel", problem.severityLevel());
            addAttribute(attributes, "impactLevel", problem.impactLevel());
            addAttribute(attributes, "status", problem.status());
            addAttribute(attributes, "startTime", renderInstant(problem.startTime()));
            addAttribute(attributes, "endTime", renderInstant(problem.endTime()));
            addAttribute(attributes, "rootCauseEntityId", problem.rootCauseEntityId());
            addAttribute(attributes, "rootCauseEntityName", problem.rootCauseEntityName());
            addAttribute(attributes, "affectedEntities", joined(problem.affectedEntities()));
            addAttribute(attributes, "impactedEntities", joined(problem.impactedEntities()));
            addAttribute(attributes, "evidenceSummary", evidenceSummary(problem.evidenceDetails()));
            addAttribute(attributes, "signalCategories", joined(problemSignalCategories(problem.evidenceDetails())));
            addAttribute(attributes, "correlationHighlights", joinWithDelimiter(problemCorrelationHighlights(problem.evidenceDetails())));

            items.add(new AnalysisEvidenceItem(
                    problem.displayId() != null
                            ? "Dynatrace problem " + problem.displayId() + " " + shortLabel(problem.title())
                            : "Dynatrace problem " + shortLabel(problem.title()),
                    List.copyOf(attributes)
            ));
        }

        return List.copyOf(items);
    }

    private List<AnalysisEvidenceItem> metricItems(List<DynatraceIncidentEvidence.MetricSummary> metrics) {
        var items = new ArrayList<AnalysisEvidenceItem>();

        for (var metric : metrics) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, "entityId", metric.entityId());
            addAttribute(attributes, "entityDisplayName", metric.entityDisplayName());
            addAttribute(attributes, "metricId", metric.metricId());
            addAttribute(attributes, "metricLabel", metric.metricLabel());
            addAttribute(attributes, "unit", metric.unit());
            addAttribute(attributes, "resolution", metric.resolution());
            addAttribute(attributes, "queryFrom", renderInstant(metric.queryFrom()));
            addAttribute(attributes, "queryTo", renderInstant(metric.queryTo()));
            addAttribute(attributes, "nonNullPoints", String.valueOf(metric.nonNullPoints()));
            addAttribute(attributes, "minValue", formatNumber(metric.minValue()));
            addAttribute(attributes, "maxValue", formatNumber(metric.maxValue()));
            addAttribute(attributes, "averageValue", formatNumber(metric.averageValue()));
            addAttribute(attributes, "lastValue", formatNumber(metric.lastValue()));

            items.add(new AnalysisEvidenceItem(
                    "Dynatrace metric " + metric.metricLabel() + " for " + shortLabel(metric.entityDisplayName()),
                    List.copyOf(attributes)
            ));
        }

        return List.copyOf(items);
    }

    private String evidenceSummary(List<DynatraceIncidentEvidence.ProblemEvidence> evidenceDetails) {
        var summary = evidenceDetails.stream()
                .map(this::formatProblemEvidence)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + " || " + right)
                .orElse(null);

        if (!StringUtils.hasText(summary) || summary.length() <= MAX_PROBLEM_EVIDENCE_SUMMARY_CHARACTERS) {
            return summary;
        }

        return summary.substring(0, MAX_PROBLEM_EVIDENCE_SUMMARY_CHARACTERS) + "...";
    }

    private List<String> problemSignalCategories(List<DynatraceIncidentEvidence.ProblemEvidence> evidenceDetails) {
        var categories = new LinkedHashSet<String>();

        for (var evidence : evidenceDetails) {
            if (isDatabaseSignal(evidence)) {
                categories.add(DATABASE_CONNECTIVITY_CATEGORY);
            }
            if (isAvailabilitySignal(evidence)) {
                categories.add(AVAILABILITY_CATEGORY);
            }
            if (isMessagingSignal(evidence)) {
                categories.add(MESSAGING_CATEGORY);
            }
            if (isLatencySignal(evidence)) {
                categories.add(LATENCY_CATEGORY);
            }
            if (isFailureRateSignal(evidence)) {
                categories.add(FAILURE_RATE_CATEGORY);
            }
        }

        return List.copyOf(categories);
    }

    private List<String> problemCorrelationHighlights(List<DynatraceIncidentEvidence.ProblemEvidence> evidenceDetails) {
        var highlights = new LinkedHashSet<String>();

        for (var evidence : evidenceDetails) {
            var highlight = formatProblemCorrelationHighlight(evidence);
            if (StringUtils.hasText(highlight)) {
                highlights.add(highlight);
            }
        }

        return highlights.stream()
                .limit(5)
                .toList();
    }

    private String formatProblemEvidence(DynatraceIncidentEvidence.ProblemEvidence evidence) {
        var parts = new ArrayList<String>();
        addPart(parts, evidence.displayName());
        addPart(parts, "type=" + evidence.evidenceType());
        addPart(parts, "rootCauseRelevant=" + evidence.rootCauseRelevant());
        addPart(parts, StringUtils.hasText(evidence.eventType()) ? "eventType=" + evidence.eventType() : null);
        addPart(parts, StringUtils.hasText(evidence.metricId()) ? "metricId=" + evidence.metricId() : null);
        addPart(parts, StringUtils.hasText(evidence.entityName()) ? "entity=" + shortLabel(evidence.entityName()) : null);

        if (evidence.valueBeforeChangePoint() != null || evidence.valueAfterChangePoint() != null) {
            addPart(parts, "change=" + formatNumber(evidence.valueBeforeChangePoint())
                    + " -> " + formatNumber(evidence.valueAfterChangePoint())
                    + (StringUtils.hasText(evidence.unit()) ? " " + evidence.unit() : ""));
        }

        return String.join(", ", parts);
    }

    private String formatProblemCorrelationHighlight(DynatraceIncidentEvidence.ProblemEvidence evidence) {
        var label = firstNonBlank(evidence.displayName(), evidence.eventType(), evidence.evidenceType());
        if (!StringUtils.hasText(label)) {
            return null;
        }

        var parts = new ArrayList<String>();
        parts.add(label.trim());

        if (StringUtils.hasText(evidence.eventType())
                && !label.equalsIgnoreCase(evidence.eventType())) {
            parts.add("[" + evidence.eventType().trim() + "]");
        } else if (StringUtils.hasText(evidence.evidenceType())
                && !label.equalsIgnoreCase(evidence.evidenceType())) {
            parts.add("[" + evidence.evidenceType().trim() + "]");
        }

        if (evidence.rootCauseRelevant()) {
            parts.add("(root cause)");
        }

        return String.join(" ", parts);
    }

    private boolean isDatabaseSignal(DynatraceIncidentEvidence.ProblemEvidence evidence) {
        var normalized = normalizedEvidenceText(evidence);
        return normalized.contains("database")
                || normalized.contains("dbconnection")
                || normalized.contains("jdbc")
                || normalized.contains("oracle")
                || normalized.contains("sql");
    }

    private boolean isAvailabilitySignal(DynatraceIncidentEvidence.ProblemEvidence evidence) {
        var normalized = normalizedEvidenceText(evidence);
        return normalized.contains("availability_evidence")
                || normalized.contains("availability")
                || normalized.contains("unavailable")
                || normalized.contains("down");
    }

    private boolean isMessagingSignal(DynatraceIncidentEvidence.ProblemEvidence evidence) {
        var normalized = normalizedEvidenceText(evidence);
        return normalized.contains("rabbitmq")
                || normalized.contains("amqp")
                || normalized.contains("queue")
                || normalized.contains("mq");
    }

    private boolean isLatencySignal(DynatraceIncidentEvidence.ProblemEvidence evidence) {
        var normalized = normalizedEvidenceText(evidence);
        return normalized.contains("response time")
                || normalized.contains("response.time")
                || normalized.contains("latency");
    }

    private boolean isFailureRateSignal(DynatraceIncidentEvidence.ProblemEvidence evidence) {
        var normalized = normalizedEvidenceText(evidence);
        return normalized.contains("failure rate")
                || normalized.contains("error rate")
                || normalized.contains("service_error_rate");
    }

    private String normalizedEvidenceText(DynatraceIncidentEvidence.ProblemEvidence evidence) {
        return String.join(" ",
                        firstNonBlank(evidence.displayName(), ""),
                        firstNonBlank(evidence.eventType(), ""),
                        firstNonBlank(evidence.evidenceType(), ""),
                        firstNonBlank(evidence.metricId(), ""),
                        firstNonBlank(evidence.entityName(), ""),
                        firstNonBlank(evidence.groupingEntityName(), ""))
                .toLowerCase(Locale.ROOT);
    }

    private String formatNumber(Double value) {
        if (value == null) {
            return null;
        }

        var rendered = String.format(Locale.ROOT, "%.2f", value);
        return rendered.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String renderInstant(Instant value) {
        return value != null ? value.toString() : null;
    }

    private String joined(List<String> values) {
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String joinWithDelimiter(List<String> values) {
        return values.isEmpty() ? null : String.join(" || ", values);
    }

    private String shortLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return "<unknown>";
        }

        return value.length() <= 96 ? value : value.substring(0, 96) + "...";
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

    private void addValue(LinkedHashSet<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private void addPart(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value);
        }
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return "";
    }

}
