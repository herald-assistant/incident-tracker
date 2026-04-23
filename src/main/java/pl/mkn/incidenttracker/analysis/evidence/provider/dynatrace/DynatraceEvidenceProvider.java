package pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentEvidence;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentQuery;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceProperties;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.ResolvedDeploymentContext;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Slf4j
public class DynatraceEvidenceProvider implements AnalysisEvidenceProvider {

    private static final int MAX_PROBLEM_EVIDENCE_SUMMARY_CHARACTERS = 800;
    private static final int MAX_COMPONENT_SUMMARY_CHARACTERS = 240;
    private static final String DATABASE_CONNECTIVITY_CATEGORY = "database-connectivity";
    private static final String AVAILABILITY_CATEGORY = "availability";
    private static final String MESSAGING_CATEGORY = "messaging";
    private static final String LATENCY_CATEGORY = "latency";
    private static final String FAILURE_RATE_CATEGORY = "failure-rate";
    private static final String ITEM_TYPE_SERVICE_MATCH = "service-match";
    private static final String ITEM_TYPE_PROBLEM = "problem";
    private static final String ITEM_TYPE_METRIC = "metric";
    private static final double HIGH_RESPONSE_TIME_THRESHOLD_MILLIS = 1_000.0d;
    private static final double LOW_SUCCESS_RATE_THRESHOLD_PERCENT = 99.0d;
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\bstatus\\s+(\\d{3})\\b");

    private final DynatraceIncidentPort dynatraceIncidentPort;
    private final DynatraceProperties dynatraceProperties;
    private final DeploymentContextResolver deploymentContextResolver;

    @Autowired
    public DynatraceEvidenceProvider(
            DynatraceIncidentPort dynatraceIncidentPort,
            DynatraceProperties dynatraceProperties,
            DeploymentContextResolver deploymentContextResolver
    ) {
        this.dynatraceIncidentPort = dynatraceIncidentPort;
        this.dynatraceProperties = dynatraceProperties;
        this.deploymentContextResolver = deploymentContextResolver;
    }

    public DynatraceEvidenceProvider(
            DynatraceIncidentPort dynatraceIncidentPort,
            DeploymentContextResolver deploymentContextResolver
    ) {
        this(dynatraceIncidentPort, configuredTestProperties(), deploymentContextResolver);
    }

    @Override
    public AnalysisEvidenceSection collect(AnalysisContext context) {
        var logEvidence = ElasticLogEvidenceView.from(context);
        if (logEvidence.isEmpty()) {
            return emptySection();
        }

        if (!dynatraceProperties.isConfigured()) {
            return statusOnlySection(
                    buildCollectionStatusItem(
                            null,
                            DynatraceRuntimeEvidenceView.CollectionStatus.DISABLED,
                            "Dynatrace runtime collection is disabled or not configured.",
                            disabledInterpretation(),
                            DynatraceRuntimeEvidenceView.CorrelationStatus.UNKNOWN,
                            0,
                            0,
                            0
                    )
            );
        }

        if (shouldSkipForDevEnvironment(
                DeploymentContextEvidenceView.from(context),
                logEvidence
        )) {
            log.info("Dynatrace enrichment skipped for dev environment correlationId={}", context.correlationId());
            return statusOnlySection(
                    buildCollectionStatusItem(
                            null,
                            DynatraceRuntimeEvidenceView.CollectionStatus.SKIPPED,
                            "Dynatrace enrichment is skipped for dev environment.",
                            skippedInterpretation(),
                            DynatraceRuntimeEvidenceView.CorrelationStatus.UNKNOWN,
                            0,
                            0,
                            0
                    )
            );
        }

        var query = DynatraceIncidentQuery.from(context.correlationId(), logEvidence);
        if (query == null) {
            return statusOnlySection(
                    buildCollectionStatusItem(
                            null,
                            DynatraceRuntimeEvidenceView.CollectionStatus.SKIPPED,
                            "Dynatrace lookup signals could not be derived from evidence.",
                            skippedInterpretation(),
                            DynatraceRuntimeEvidenceView.CorrelationStatus.UNKNOWN,
                            0,
                            0,
                            0
                    )
            );
        }

        try {
            return collectedSection(query, dynatraceIncidentPort.loadIncidentEvidence(query));
        } catch (RuntimeException exception) {
            log.warn(
                    "Dynatrace enrichment skipped correlationId={} reason={}",
                    context.correlationId(),
                    exception.getMessage()
            );
            log.debug("Dynatrace enrichment failure details correlationId={}", context.correlationId(), exception);
            return statusOnlySection(
                    buildCollectionStatusItem(
                            query,
                            DynatraceRuntimeEvidenceView.CollectionStatus.UNAVAILABLE,
                            humanizedFailureReason(exception),
                            unavailableInterpretation(),
                            DynatraceRuntimeEvidenceView.CorrelationStatus.UNKNOWN,
                            0,
                            0,
                            0
                    )
            );
        }
    }

    @Override
    public AnalysisEvidenceReference producedEvidence() {
        return DynatraceRuntimeEvidenceView.EVIDENCE_REFERENCE;
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

    private AnalysisEvidenceSection statusOnlySection(AnalysisEvidenceItem item) {
        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.of(item)
        );
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
                    .map(ResolvedDeploymentContext::environment)
                    .filter(StringUtils::hasText)
                    .anyMatch(environment -> environment.startsWith("dev"));
        }

        return logEvidence.entries().stream()
                .map(deploymentContextResolver::resolve)
                .filter(java.util.Objects::nonNull)
                .map(ResolvedDeploymentContext::environment)
                .filter(StringUtils::hasText)
                .anyMatch(environment -> environment.startsWith("dev"));
    }

    private AnalysisEvidenceSection collectedSection(
            DynatraceIncidentQuery query,
            DynatraceIncidentEvidence evidence
    ) {
        var resolvedEvidence = evidence != null ? evidence : DynatraceIncidentEvidence.empty();
        var abnormalMetrics = resolvedEvidence.metrics().stream()
                .filter(this::isRelevantMetricSignal)
                .toList();
        var componentSummaries = buildComponentSummaries(
                resolvedEvidence.serviceMatches(),
                resolvedEvidence.problems(),
                abnormalMetrics
        );
        var problematicComponentCount = (int) componentSummaries.stream()
                .filter(DynatraceComponentSummary::hasSignals)
                .count();
        var healthyComponentCount = componentSummaries.size() - problematicComponentCount;
        var correlationStatus = componentSummaries.isEmpty()
                ? DynatraceRuntimeEvidenceView.CorrelationStatus.NO_MATCH
                : DynatraceRuntimeEvidenceView.CorrelationStatus.MATCHED;

        var items = new ArrayList<AnalysisEvidenceItem>();
        items.add(buildCollectionStatusItem(
                query,
                DynatraceRuntimeEvidenceView.CollectionStatus.COLLECTED,
                "Dynatrace query completed successfully.",
                collectedInterpretation(correlationStatus),
                correlationStatus,
                componentSummaries.size(),
                problematicComponentCount,
                healthyComponentCount
        ));
        items.addAll(componentStatusItems(componentSummaries));
        items.addAll(serviceMatchItems(query, resolvedEvidence.serviceMatches(), componentSummaries));
        items.addAll(problemItems(resolvedEvidence.problems(), componentSummaries));
        items.addAll(metricItems(abnormalMetrics, componentSummaries));
        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.copyOf(items)
        );
    }

    private List<AnalysisEvidenceItem> serviceMatchItems(
            DynatraceIncidentQuery query,
            List<DynatraceIncidentEvidence.ServiceMatch> matches,
            List<DynatraceComponentSummary> componentSummaries
    ) {
        var items = new ArrayList<AnalysisEvidenceItem>();
        var componentNamesByEntityId = componentNamesByEntityId(componentSummaries);

        for (var match : matches) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, DynatraceRuntimeEvidenceView.ITEM_TYPE_ATTRIBUTE, ITEM_TYPE_SERVICE_MATCH);
            addAttribute(attributes, "componentName", componentNamesByEntityId.get(match.entityId()));
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

    private List<AnalysisEvidenceItem> problemItems(
            List<DynatraceIncidentEvidence.ProblemSummary> problems,
            List<DynatraceComponentSummary> componentSummaries
    ) {
        var items = new ArrayList<AnalysisEvidenceItem>();

        for (var problem : problems) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, DynatraceRuntimeEvidenceView.ITEM_TYPE_ATTRIBUTE, ITEM_TYPE_PROBLEM);
            addAttribute(attributes, "componentName", componentNameForProblem(problem, componentSummaries));
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

    private List<AnalysisEvidenceItem> metricItems(
            List<DynatraceIncidentEvidence.MetricSummary> metrics,
            List<DynatraceComponentSummary> componentSummaries
    ) {
        var items = new ArrayList<AnalysisEvidenceItem>();
        var componentNamesByEntityId = componentNamesByEntityId(componentSummaries);

        for (var metric : metrics) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, DynatraceRuntimeEvidenceView.ITEM_TYPE_ATTRIBUTE, ITEM_TYPE_METRIC);
            addAttribute(attributes, "componentName", componentNamesByEntityId.get(metric.entityId()));
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

    private List<AnalysisEvidenceItem> componentStatusItems(List<DynatraceComponentSummary> componentSummaries) {
        var items = new ArrayList<AnalysisEvidenceItem>();

        for (var summary : componentSummaries) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(
                    attributes,
                    DynatraceRuntimeEvidenceView.ITEM_TYPE_ATTRIBUTE,
                    DynatraceRuntimeEvidenceView.ITEM_TYPE_COMPONENT_STATUS
            );
            addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_COMPONENT_NAME, summary.componentName());
            addAttribute(
                    attributes,
                    DynatraceRuntimeEvidenceView.ATTRIBUTE_CORRELATION_STATUS,
                    DynatraceRuntimeEvidenceView.CorrelationStatus.MATCHED.name()
            );
            addAttribute(
                    attributes,
                    DynatraceRuntimeEvidenceView.ATTRIBUTE_COMPONENT_SIGNAL_STATUS,
                    summary.componentSignalStatus()
            );
            addAttribute(attributes, "entityIds", joined(summary.entityIds()));
            addAttribute(attributes, "matchedContainers", joined(summary.containers()));
            addAttribute(attributes, "matchedServiceNames", joined(summary.serviceNames()));
            addAttribute(attributes, "problemCount", String.valueOf(summary.problems().size()));
            addAttribute(attributes, "abnormalMetricCount", String.valueOf(summary.abnormalMetrics().size()));
            addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_SIGNAL_CATEGORIES, joined(summary.signalCategories()));
            addAttribute(
                    attributes,
                    DynatraceRuntimeEvidenceView.ATTRIBUTE_CORRELATION_HIGHLIGHTS,
                    joinWithDelimiter(summary.correlationHighlights())
            );
            addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_SUMMARY, summary.summary());

            if (!summary.hasSignals()) {
                addAttribute(
                        attributes,
                        DynatraceRuntimeEvidenceView.ATTRIBUTE_INTERPRETATION,
                        "Dynatrace returned data for this component and found no problems or abnormal metrics in the incident window."
                );
            }

            var primaryProblem = primaryProblem(summary.problems());
            if (primaryProblem != null) {
                addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_PROBLEM_DISPLAY_ID, primaryProblem.displayId());
                addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_PROBLEM_TITLE, primaryProblem.title());
            }

            items.add(new AnalysisEvidenceItem(
                    "Dynatrace correlated component " + shortLabel(summary.componentName()),
                    List.copyOf(attributes)
            ));
        }

        return List.copyOf(items);
    }

    private AnalysisEvidenceItem buildCollectionStatusItem(
            DynatraceIncidentQuery query,
            DynatraceRuntimeEvidenceView.CollectionStatus collectionStatus,
            String reason,
            String interpretation,
            DynatraceRuntimeEvidenceView.CorrelationStatus correlationStatus,
            int matchedComponentCount,
            int problematicComponentCount,
            int healthyComponentCount
    ) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(
                attributes,
                DynatraceRuntimeEvidenceView.ITEM_TYPE_ATTRIBUTE,
                DynatraceRuntimeEvidenceView.ITEM_TYPE_COLLECTION_STATUS
        );
        addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_COLLECTION_STATUS, collectionStatus.name());
        addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_COLLECTION_REASON, reason);
        addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_INTERPRETATION, interpretation);
        addAttribute(attributes, DynatraceRuntimeEvidenceView.ATTRIBUTE_CORRELATION_STATUS, correlationStatus.name());

        if (query != null) {
            addAttribute(attributes, "incidentStart", renderInstant(query.incidentStart()));
            addAttribute(attributes, "incidentEnd", renderInstant(query.incidentEnd()));
        }

        addAttribute(attributes, "matchedComponentCount", String.valueOf(matchedComponentCount));
        addAttribute(attributes, "problematicComponentCount", String.valueOf(problematicComponentCount));
        addAttribute(attributes, "healthyComponentCount", String.valueOf(healthyComponentCount));
        return new AnalysisEvidenceItem("Dynatrace collection status", List.copyOf(attributes));
    }

    private List<DynatraceComponentSummary> buildComponentSummaries(
            List<DynatraceIncidentEvidence.ServiceMatch> matches,
            List<DynatraceIncidentEvidence.ProblemSummary> problems,
            List<DynatraceIncidentEvidence.MetricSummary> abnormalMetrics
    ) {
        var groupedMatches = new LinkedHashMap<String, List<DynatraceIncidentEvidence.ServiceMatch>>();

        for (var match : matches) {
            groupedMatches.computeIfAbsent(resolveComponentName(match), ignored -> new ArrayList<>())
                    .add(match);
        }

        var summaries = new ArrayList<DynatraceComponentSummary>();
        for (var entry : groupedMatches.entrySet()) {
            var componentName = entry.getKey();
            var componentMatches = List.copyOf(entry.getValue());
            var componentProblems = problems.stream()
                    .filter(problem -> componentMatchesProblem(componentMatches, componentName, problem))
                    .toList();
            var componentMetrics = abnormalMetrics.stream()
                    .filter(metric -> componentMatchesMetric(componentMatches, componentName, metric))
                    .toList();

            summaries.add(new DynatraceComponentSummary(
                    componentName,
                    componentMatches,
                    componentProblems,
                    componentMetrics,
                    collectSignalCategories(componentProblems),
                    collectCorrelationHighlights(componentProblems),
                    summarizeComponentSignals(componentProblems, componentMetrics)
            ));
        }

        return summaries.stream()
                .sorted(Comparator
                        .comparing(DynatraceComponentSummary::hasSignals)
                        .reversed()
                        .thenComparing(DynatraceComponentSummary::componentName))
                .toList();
    }

    private boolean componentMatchesProblem(
            List<DynatraceIncidentEvidence.ServiceMatch> componentMatches,
            String componentName,
            DynatraceIncidentEvidence.ProblemSummary problem
    ) {
        for (var match : componentMatches) {
            if (StringUtils.hasText(problem.rootCauseEntityId())
                    && problem.rootCauseEntityId().equals(match.entityId())) {
                return true;
            }
        }

        if (matchesComponentText(componentName, problem.rootCauseEntityName())) {
            return true;
        }

        for (var entity : problem.affectedEntities()) {
            if (matchesComponentText(componentName, entity)) {
                return true;
            }
        }

        for (var entity : problem.impactedEntities()) {
            if (matchesComponentText(componentName, entity)) {
                return true;
            }
        }

        return componentMatches.stream().anyMatch(match ->
                matchesComponentText(match.displayName(), problem.rootCauseEntityName())
                        || match.matchedServiceNames().stream().anyMatch(value -> matchesComponentText(value, problem.rootCauseEntityName()))
        );
    }

    private boolean componentMatchesMetric(
            List<DynatraceIncidentEvidence.ServiceMatch> componentMatches,
            String componentName,
            DynatraceIncidentEvidence.MetricSummary metric
    ) {
        for (var match : componentMatches) {
            if (StringUtils.hasText(metric.entityId()) && metric.entityId().equals(match.entityId())) {
                return true;
            }
        }

        if (matchesComponentText(componentName, metric.entityDisplayName())) {
            return true;
        }

        return componentMatches.stream().anyMatch(match ->
                matchesComponentText(match.displayName(), metric.entityDisplayName())
                        || match.matchedServiceNames().stream().anyMatch(value -> matchesComponentText(value, metric.entityDisplayName()))
        );
    }

    private Map<String, String> componentNamesByEntityId(List<DynatraceComponentSummary> componentSummaries) {
        var componentNamesByEntityId = new LinkedHashMap<String, String>();

        for (var summary : componentSummaries) {
            for (var entityId : summary.entityIds()) {
                componentNamesByEntityId.put(entityId, summary.componentName());
            }
        }

        return componentNamesByEntityId;
    }

    private String componentNameForProblem(
            DynatraceIncidentEvidence.ProblemSummary problem,
            List<DynatraceComponentSummary> componentSummaries
    ) {
        return componentSummaries.stream()
                .filter(summary -> componentMatchesProblem(summary.matches(), summary.componentName(), problem))
                .map(DynatraceComponentSummary::componentName)
                .findFirst()
                .orElse(null);
    }

    private List<String> collectSignalCategories(List<DynatraceIncidentEvidence.ProblemSummary> problems) {
        var categories = new LinkedHashSet<String>();

        for (var problem : problems) {
            categories.addAll(problemSignalCategories(problem.evidenceDetails()));
        }

        return List.copyOf(categories);
    }

    private List<String> collectCorrelationHighlights(List<DynatraceIncidentEvidence.ProblemSummary> problems) {
        var highlights = new LinkedHashSet<String>();

        for (var problem : problems) {
            highlights.addAll(problemCorrelationHighlights(problem.evidenceDetails()));
        }

        return highlights.stream()
                .limit(5)
                .toList();
    }

    private String summarizeComponentSignals(
            List<DynatraceIncidentEvidence.ProblemSummary> problems,
            List<DynatraceIncidentEvidence.MetricSummary> abnormalMetrics
    ) {
        var parts = new ArrayList<String>();
        var primaryProblem = primaryProblem(problems);
        if (primaryProblem != null) {
            addPart(parts, evidenceSummary(primaryProblem.evidenceDetails()));
        }

        abnormalMetrics.stream()
                .map(this::formatMetricSignalSummary)
                .filter(StringUtils::hasText)
                .limit(2)
                .forEach(parts::add);

        var summary = joinWithDelimiter(parts);
        if (!StringUtils.hasText(summary) || summary.length() <= MAX_COMPONENT_SUMMARY_CHARACTERS) {
            return summary;
        }

        return summary.substring(0, MAX_COMPONENT_SUMMARY_CHARACTERS) + "...";
    }

    private DynatraceIncidentEvidence.ProblemSummary primaryProblem(
            List<DynatraceIncidentEvidence.ProblemSummary> problems
    ) {
        return problems.stream()
                .sorted(Comparator
                        .comparing(DynatraceIncidentEvidence.ProblemSummary::startTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DynatraceIncidentEvidence.ProblemSummary::displayId, Comparator.nullsLast(String::compareTo)))
                .findFirst()
                .orElse(null);
    }

    private String resolveComponentName(DynatraceIncidentEvidence.ServiceMatch match) {
        return firstNonBlank(
                firstValue(match.matchedServiceNames()),
                firstValue(match.matchedContainers()),
                shortDynatraceDisplayName(match.displayName()),
                match.displayName()
        );
    }

    private String shortDynatraceDisplayName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var segments = value.split("\\|\\|");
        for (int index = segments.length - 1; index >= 0; index--) {
            var segment = segments[index].trim();
            if (StringUtils.hasText(segment)) {
                return segment;
            }
        }

        return value;
    }

    private String firstValue(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private boolean isRelevantMetricSignal(DynatraceIncidentEvidence.MetricSummary metric) {
        var label = firstNonBlank(metric.metricLabel(), metric.metricId()).toLowerCase(Locale.ROOT);

        if (label.contains("response.time")) {
            return greaterThan(metric.maxValue(), HIGH_RESPONSE_TIME_THRESHOLD_MILLIS)
                    || greaterThan(metric.averageValue(), HIGH_RESPONSE_TIME_THRESHOLD_MILLIS / 2.0d);
        }

        if (label.contains("success.rate")) {
            return lowerThan(metric.minValue(), LOW_SUCCESS_RATE_THRESHOLD_PERCENT)
                    || lowerThan(metric.lastValue(), LOW_SUCCESS_RATE_THRESHOLD_PERCENT);
        }

        if (label.contains("errors.total")
                || label.contains("errors.4xx")
                || label.contains("errors.5xx")) {
            return greaterThan(metric.maxValue(), 0.0d);
        }

        return greaterThan(metric.maxValue(), 0.0d);
    }

    private boolean greaterThan(Double value, double threshold) {
        return value != null && value > threshold;
    }

    private boolean lowerThan(Double value, double threshold) {
        return value != null && value < threshold;
    }

    private String formatMetricSignalSummary(DynatraceIncidentEvidence.MetricSummary metric) {
        var label = firstNonBlank(metric.metricLabel(), metric.metricId());
        if (!StringUtils.hasText(label)) {
            return null;
        }

        var normalized = label.toLowerCase(Locale.ROOT);
        if (normalized.contains("response.time")) {
            return "response time peaked at " + firstNonBlank(
                    formatMetricValue(metric.maxValue(), metric.unit()),
                    formatMetricValue(metric.averageValue(), metric.unit())
            );
        }

        if (normalized.contains("success.rate")) {
            return "success rate dropped to " + firstNonBlank(
                    formatMetricValue(metric.minValue(), metric.unit()),
                    formatMetricValue(metric.lastValue(), metric.unit())
            );
        }

        if (normalized.contains("errors")) {
            return label + " peaked at " + formatMetricValue(metric.maxValue(), metric.unit());
        }

        return label + " reached " + firstNonBlank(
                formatMetricValue(metric.maxValue(), metric.unit()),
                formatMetricValue(metric.lastValue(), metric.unit())
        );
    }

    private String formatMetricValue(Double value, String unit) {
        if (value == null) {
            return null;
        }

        var renderedValue = formatNumber(value);
        return StringUtils.hasText(unit) ? renderedValue + " " + unit : renderedValue;
    }

    private String humanizedFailureReason(RuntimeException exception) {
        var rawMessage = firstNonBlank(exception.getMessage(), exception.getClass().getSimpleName());
        var matcher = HTTP_STATUS_PATTERN.matcher(rawMessage);
        if (matcher.find()) {
            return "Dynatrace API request failed with HTTP " + matcher.group(1);
        }

        return shortLabel(rawMessage);
    }

    private String collectedInterpretation(DynatraceRuntimeEvidenceView.CorrelationStatus correlationStatus) {
        if (DynatraceRuntimeEvidenceView.CorrelationStatus.NO_MATCH.equals(correlationStatus)) {
            return "Dynatrace query completed successfully but no correlated component was matched in the incident window. Missing Dynatrace problems or metrics must be treated as inconclusive, not as healthy runtime.";
        }

        return "Dynatrace query completed successfully. Missing problems or abnormal metrics for a matched component can be treated as absence of Dynatrace-confirmed issues in the incident window.";
    }

    private String unavailableInterpretation() {
        return "Dynatrace visibility is unavailable for this incident. Missing Dynatrace metrics, problems, or component signals must be treated as lack of data, not as healthy runtime.";
    }

    private String disabledInterpretation() {
        return "Dynatrace runtime collection is disabled for this incident scope or application configuration. Missing Dynatrace metrics, problems, or component signals must be treated as lack of configured visibility, not as healthy runtime.";
    }

    private String skippedInterpretation() {
        return "Dynatrace runtime collection was skipped for this incident. Missing Dynatrace metrics, problems, or component signals must be treated as lack of data, not as healthy runtime.";
    }

    private boolean matchesComponentText(String left, String right) {
        var normalizedLeft = normalizeLookupValue(left);
        var normalizedRight = normalizeLookupValue(right);
        if (!StringUtils.hasText(normalizedLeft) || !StringUtils.hasText(normalizedRight)) {
            return false;
        }

        return normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft);
    }

    private String normalizeLookupValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.toLowerCase(Locale.ROOT)
                .replace('\u00A0', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
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

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
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

    private static DynatraceProperties configuredTestProperties() {
        var properties = new DynatraceProperties();
        properties.setBaseUrl("https://dynatrace.test");
        properties.setApiToken("test-token");
        return properties;
    }

    private record DynatraceComponentSummary(
            String componentName,
            List<DynatraceIncidentEvidence.ServiceMatch> matches,
            List<DynatraceIncidentEvidence.ProblemSummary> problems,
            List<DynatraceIncidentEvidence.MetricSummary> abnormalMetrics,
            List<String> signalCategories,
            List<String> correlationHighlights,
            String summary
    ) {

        boolean hasSignals() {
            return !problems.isEmpty() || !abnormalMetrics.isEmpty();
        }

        String componentSignalStatus() {
            return hasSignals()
                    ? DynatraceRuntimeEvidenceView.ComponentSignalStatus.SIGNALS_PRESENT.name()
                    : DynatraceRuntimeEvidenceView.ComponentSignalStatus.NO_RELEVANT_SIGNALS.name();
        }

        List<String> entityIds() {
            return matches.stream()
                    .map(DynatraceIncidentEvidence.ServiceMatch::entityId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }

        List<String> containers() {
            return matches.stream()
                    .flatMap(match -> match.matchedContainers().stream())
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }

        List<String> serviceNames() {
            return matches.stream()
                    .flatMap(match -> match.matchedServiceNames().stream())
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
    }

}
