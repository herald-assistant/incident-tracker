package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DynatraceRestIncidentAdapter implements DynatraceIncidentPort {

    private static final String PROBLEM_SELECTOR = "severityLevel(\"ERROR\",\"PERFORMANCE\")";
    private static final double MICROSECONDS_TO_MILLISECONDS = 1.0d / 1_000.0d;
    private static final List<String> LOW_VALUE_ENTITY_SUFFIXES = List.of(
            "controller",
            "resource",
            "filter",
            "logger",
            "exceptionhandler",
            "listener"
    );
    private static final List<MetricDefinition> METRIC_DEFINITIONS = List.of(
            new MetricDefinition("builtin:service.response.time:percentile(95)", "service.response.time.p95", "ms", true, MICROSECONDS_TO_MILLISECONDS),
            new MetricDefinition("builtin:service.errors.total.count:value:rate(1m):default(0)", "service.errors.total.rate", "count/min", false, 1.0d),
            new MetricDefinition("builtin:service.errors.fourxx.count:value:rate(1m):default(0)", "service.errors.4xx.rate", "count/min", false, 1.0d),
            new MetricDefinition("builtin:service.errors.fivexx.count:value:rate(1m):default(0)", "service.errors.5xx.rate", "count/min", false, 1.0d),
            new MetricDefinition("builtin:service.successes.server.rate:avg", "service.success.rate", "%", true, 1.0d)
    );

    private final DynatraceProperties properties;
    private final DynatraceRestClientFactory restClientFactory;

    @Override
    public DynatraceIncidentEvidence loadIncidentEvidence(DynatraceIncidentQuery query) {
        if (!properties.isConfigured() || !query.hasTimeWindow() || !query.hasLookupSignals()) {
            return DynatraceIncidentEvidence.empty();
        }

        var lookupFrom = query.incidentStart().minus(properties.getQueryPaddingBefore());
        var lookupTo = query.incidentEnd().plus(properties.getQueryPaddingAfter());
        var client = restClientFactory.create(properties);
        var rankedMatches = rankServiceMatches(fetchServiceEntities(client), query);

        if (rankedMatches.isEmpty()) {
            return DynatraceIncidentEvidence.empty();
        }

        var limitedMatches = rankedMatches.stream()
                .limit(Math.max(1, properties.getEntityCandidateLimit()))
                .toList();
        var problems = fetchProblems(client, limitedMatches, lookupFrom, lookupTo);
        var metrics = fetchMetrics(client, limitedMatches, problems, lookupFrom, lookupTo);

        return new DynatraceIncidentEvidence(limitedMatches, problems, metrics);
    }

    private List<DynatraceEntity> fetchServiceEntities(RestClient client) {
        var entities = new ArrayList<DynatraceEntity>();
        String nextPageKey = null;

        for (int page = 0; page < Math.max(1, properties.getEntityFetchMaxPages()); page++) {
            var root = getJson(client, entitiesUri(nextPageKey));
            var entityNodes = root.path("entities");
            if (entityNodes.isArray()) {
                for (var entityNode : entityNodes) {
                    var entityId = text(entityNode, "entityId");
                    var type = text(entityNode, "type");
                    var displayName = text(entityNode, "displayName");
                    if (StringUtils.hasText(entityId) && "SERVICE".equalsIgnoreCase(type)) {
                        entities.add(new DynatraceEntity(entityId, displayName));
                    }
                }
            }

            nextPageKey = text(root, "nextPageKey");
            if (!StringUtils.hasText(nextPageKey)) {
                break;
            }
        }

        return List.copyOf(entities);
    }

    private List<DynatraceIncidentEvidence.ServiceMatch> rankServiceMatches(
            List<DynatraceEntity> entities,
            DynatraceIncidentQuery query
    ) {
        return entities.stream()
                .map(entity -> rankServiceMatch(entity, query))
                .filter(match -> match != null)
                .sorted(Comparator
                        .comparingInt(DynatraceIncidentEvidence.ServiceMatch::matchScore)
                        .reversed()
                        .thenComparing(match -> isLowValueEntity(match.displayName()))
                        .thenComparing(match -> match.displayName() != null ? match.displayName().length() : Integer.MAX_VALUE))
                .toList();
    }

    private DynatraceIncidentEvidence.ServiceMatch rankServiceMatch(
            DynatraceEntity entity,
            DynatraceIncidentQuery query
    ) {
        var normalizedDisplayName = normalizeSearchText(entity.displayName());
        if (!StringUtils.hasText(normalizedDisplayName)) {
            return null;
        }

        var matchedNamespaces = matchingValues(query.namespaces(), normalizedDisplayName);
        var matchedPods = matchingValues(query.podNames(), normalizedDisplayName);
        var matchedContainers = matchingValues(query.containerNames(), normalizedDisplayName);
        var matchedServiceNames = matchingValues(query.serviceNames(), normalizedDisplayName);

        if (matchedPods.isEmpty() && matchedContainers.isEmpty() && matchedServiceNames.isEmpty()) {
            return null;
        }

        int score = matchedNamespaces.size() * 60
                + matchedPods.size() * 70
                + matchedContainers.size() * 90
                + matchedServiceNames.size() * 120;

        var terminalSegment = normalizeSearchText(terminalSegment(entity.displayName()));
        if (matchesAny(terminalSegment, query.serviceNames())) {
            score += 140;
        }
        if (matchesAny(terminalSegment, query.containerNames())) {
            score += 120;
        }
        if (isLowValueEntity(entity.displayName())) {
            score -= 80;
        }
        if (normalizedDisplayName.contains(" requests executed in background threads ")) {
            score -= 40;
        }

        if (score <= 0) {
            return null;
        }

        return new DynatraceIncidentEvidence.ServiceMatch(
                entity.entityId(),
                entity.displayName(),
                score,
                matchedNamespaces,
                matchedPods,
                matchedContainers,
                matchedServiceNames
        );
    }

    private List<DynatraceIncidentEvidence.ProblemSummary> fetchProblems(
            RestClient client,
            List<DynatraceIncidentEvidence.ServiceMatch> matches,
            Instant from,
            Instant to
    ) {
        var problemsById = new LinkedHashMap<String, DynatraceIncidentEvidence.ProblemSummary>();

        for (var match : matches) {
            var root = getJson(client, problemsUri(match.entityId(), from, to));
            var problemNodes = root.path("problems");
            if (!problemNodes.isArray()) {
                continue;
            }

            for (var problemNode : problemNodes) {
                var problem = mapProblem(problemNode);
                if (problem != null) {
                    problemsById.putIfAbsent(problem.problemId(), problem);
                }
            }
        }

        return problemsById.values().stream()
                .sorted(Comparator
                        .comparing(DynatraceIncidentEvidence.ProblemSummary::startTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DynatraceIncidentEvidence.ProblemSummary::displayId, Comparator.nullsLast(String::compareTo)))
                .limit(Math.max(1, properties.getProblemLimit()))
                .toList();
    }

    private DynatraceIncidentEvidence.ProblemSummary mapProblem(JsonNode problemNode) {
        var problemId = text(problemNode, "problemId");
        if (!StringUtils.hasText(problemId)) {
            return null;
        }

        return new DynatraceIncidentEvidence.ProblemSummary(
                problemId,
                text(problemNode, "displayId"),
                text(problemNode, "title"),
                text(problemNode, "impactLevel"),
                text(problemNode, "severityLevel"),
                text(problemNode, "status"),
                instant(problemNode, "startTime"),
                instant(problemNode, "endTime"),
                text(problemNode.path("rootCauseEntity").path("entityId"), "id"),
                text(problemNode.path("rootCauseEntity"), "name"),
                entityNames(problemNode.path("affectedEntities")),
                entityNames(problemNode.path("impactedEntities")),
                mapProblemEvidence(problemNode.path("evidenceDetails").path("details"))
        );
    }

    private List<DynatraceIncidentEvidence.ProblemEvidence> mapProblemEvidence(JsonNode detailsNode) {
        if (!detailsNode.isArray()) {
            return List.of();
        }

        var evidence = new ArrayList<DynatraceIncidentEvidence.ProblemEvidence>();
        for (var detailNode : detailsNode) {
            evidence.add(new DynatraceIncidentEvidence.ProblemEvidence(
                    text(detailNode, "evidenceType"),
                    text(detailNode, "displayName"),
                    text(detailNode.path("entity"), "name"),
                    text(detailNode.path("groupingEntity"), "name"),
                    detailNode.path("rootCauseRelevant").asBoolean(false),
                    text(detailNode, "eventType"),
                    text(detailNode, "metricId"),
                    text(detailNode, "unit"),
                    nullableDouble(detailNode, "valueBeforeChangePoint"),
                    nullableDouble(detailNode, "valueAfterChangePoint"),
                    instant(detailNode, "startTime"),
                    instant(detailNode, "endTime")
            ));
        }

        return evidence.stream()
                .sorted(Comparator
                        .comparing(DynatraceIncidentEvidence.ProblemEvidence::rootCauseRelevant)
                        .reversed()
                        .thenComparing(DynatraceIncidentEvidence.ProblemEvidence::displayName, Comparator.nullsLast(String::compareTo)))
                .limit(Math.max(1, properties.getProblemEvidenceLimit()))
                .toList();
    }

    private List<DynatraceIncidentEvidence.MetricSummary> fetchMetrics(
            RestClient client,
            List<DynatraceIncidentEvidence.ServiceMatch> matches,
            List<DynatraceIncidentEvidence.ProblemSummary> problems,
            Instant from,
            Instant to
    ) {
        var entityDisplayNames = new LinkedHashMap<String, String>();
        var metricEntityIds = new LinkedHashSet<String>();

        for (var match : matches) {
            entityDisplayNames.put(match.entityId(), match.displayName());
            metricEntityIds.add(match.entityId());
            if (metricEntityIds.size() >= Math.max(1, properties.getMetricEntityLimit())) {
                break;
            }
        }

        for (var problem : problems) {
            if (metricEntityIds.size() >= Math.max(1, properties.getMetricEntityLimit())) {
                break;
            }
            if (StringUtils.hasText(problem.rootCauseEntityId())) {
                metricEntityIds.add(problem.rootCauseEntityId());
                entityDisplayNames.putIfAbsent(problem.rootCauseEntityId(), problem.rootCauseEntityName());
            }
        }

        var metrics = new ArrayList<DynatraceIncidentEvidence.MetricSummary>();
        for (var entityId : metricEntityIds) {
            var displayName = entityDisplayNames.getOrDefault(entityId, entityId);
            for (var definition : METRIC_DEFINITIONS) {
                var metric = fetchMetric(client, entityId, displayName, definition, from, to);
                if (shouldInclude(metric, definition)) {
                    metrics.add(metric);
                }
            }
        }

        return List.copyOf(metrics);
    }

    private DynatraceIncidentEvidence.MetricSummary fetchMetric(
            RestClient client,
            String entityId,
            String entityDisplayName,
            MetricDefinition definition,
            Instant from,
            Instant to
    ) {
        var root = getJson(client, metricsUri(entityId, definition.selector(), from, to));
        var resolution = text(root, "resolution");
        var resultNodes = root.path("result");
        if (!resultNodes.isArray() || resultNodes.isEmpty()) {
            return null;
        }

        JsonNode seriesNode = null;
        var dataNodes = resultNodes.get(0).path("data");
        if (dataNodes.isArray()) {
            for (var dataNode : dataNodes) {
                var dimensionEntity = text(dataNode.path("dimensionMap"), "dt.entity.service");
                if (!StringUtils.hasText(dimensionEntity) || entityId.equals(dimensionEntity)) {
                    seriesNode = dataNode;
                    break;
                }
            }
        }

        if (seriesNode == null) {
            return null;
        }

        var values = new ArrayList<Double>();
        for (var valueNode : seriesNode.path("values")) {
            if (!valueNode.isNull()) {
                values.add(valueNode.asDouble());
            }
        }

        if (values.isEmpty()) {
            return null;
        }

        var min = values.stream().min(Double::compareTo).orElse(null);
        var max = values.stream().max(Double::compareTo).orElse(null);
        var avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        var last = values.get(values.size() - 1);

        return new DynatraceIncidentEvidence.MetricSummary(
                entityId,
                entityDisplayName,
                definition.selector(),
                definition.label(),
                definition.unit(),
                StringUtils.hasText(resolution) ? resolution : properties.getMetricResolution(),
                from,
                to,
                values.size(),
                normalizeMetricValue(min, definition),
                normalizeMetricValue(max, definition),
                Double.isNaN(avg) ? null : normalizeMetricValue(avg, definition),
                normalizeMetricValue(last, definition)
        );
    }

    private boolean shouldInclude(
            DynatraceIncidentEvidence.MetricSummary metric,
            MetricDefinition definition
    ) {
        return metric != null
                && metric.nonNullPoints() > 0
                && (definition.alwaysInclude()
                || (metric.maxValue() != null && metric.maxValue() > 0.0d));
    }

    private Double normalizeMetricValue(Double value, MetricDefinition definition) {
        if (value == null) {
            return null;
        }

        return value * definition.valueMultiplier();
    }

    private JsonNode getJson(RestClient client, URI uri) {
        try {
            return client.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Dynatrace request failed for " + uri + " with status " + exception.getStatusCode().value(),
                    exception
            );
        }
    }

    private URI entitiesUri(String nextPageKey) {
        var uri = new StringBuilder(normalizeBaseUrl(properties.getBaseUrl()))
                .append("/api/v2/entities?entitySelector=")
                .append(encodeQueryParam("type(\"SERVICE\")"))
                .append("&pageSize=")
                .append(properties.getEntityPageSize());

        if (StringUtils.hasText(nextPageKey)) {
            uri.append("&nextPageKey=").append(encodeQueryParam(nextPageKey));
        }

        return URI.create(uri.toString());
    }

    private URI problemsUri(String entityId, Instant from, Instant to) {
        return URI.create(normalizeBaseUrl(properties.getBaseUrl())
                + "/api/v2/problems?from=" + from.toEpochMilli()
                + "&to=" + to.toEpochMilli()
                + "&problemSelector=" + encodeQueryParam(PROBLEM_SELECTOR)
                + "&entitySelector=" + encodeQueryParam("entityId(\"" + entityId + "\")")
                + "&fields=" + encodeQueryParam("evidenceDetails,impactAnalysis")
                + "&sort=" + encodeQueryParam("-startTime")
                + "&pageSize=" + properties.getProblemPageSize());
    }

    private URI metricsUri(String entityId, String metricSelector, Instant from, Instant to) {
        return URI.create(normalizeBaseUrl(properties.getBaseUrl())
                + "/api/v2/metrics/query?metricSelector=" + encodeQueryParam(metricSelector)
                + "&entitySelector=" + encodeQueryParam("entityId(\"" + entityId + "\")")
                + "&from=" + from.toEpochMilli()
                + "&to=" + to.toEpochMilli()
                + "&resolution=" + encodeQueryParam(properties.getMetricResolution()));
    }

    private List<String> entityNames(JsonNode entitiesNode) {
        if (!entitiesNode.isArray()) {
            return List.of();
        }

        var values = new ArrayList<String>();
        for (var entityNode : entitiesNode) {
            var name = text(entityNode, "name");
            if (StringUtils.hasText(name) && !values.contains(name)) {
                values.add(name);
            }
        }
        return List.copyOf(values);
    }

    private List<String> matchingValues(List<String> values, String normalizedDisplayName) {
        return values.stream()
                .filter(StringUtils::hasText)
                .filter(value -> normalizedDisplayName.contains(normalizeSearchText(value)))
                .toList();
    }

    private boolean matchesAny(String normalizedTarget, List<String> values) {
        if (!StringUtils.hasText(normalizedTarget)) {
            return false;
        }

        return values.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeSearchText)
                .anyMatch(normalizedTarget::contains);
    }

    private boolean isLowValueEntity(String displayName) {
        var terminalSegment = normalizeSearchText(terminalSegment(displayName)).trim();
        return LOW_VALUE_ENTITY_SUFFIXES.stream()
                .anyMatch(terminalSegment::endsWith);
    }

    private String terminalSegment(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            return null;
        }

        var segments = displayName.split("\\|\\|");
        return segments[segments.length - 1].trim();
    }

    private String normalizeSearchText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return " " + value
                .replace('\u00A0', ' ')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim() + " ";
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }

        var value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private Instant instant(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }

        var value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || !value.canConvertToLong()) {
            return null;
        }

        return Instant.ofEpochMilli(value.asLong());
    }

    private Double nullableDouble(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }

        var value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asDouble();
    }

    private String normalizeBaseUrl(String baseUrl) {
        var normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private record DynatraceEntity(
            String entityId,
            String displayName
    ) {
    }

    private record MetricDefinition(
            String selector,
            String label,
            String unit,
            boolean alwaysInclude,
            double valueMultiplier
    ) {
    }

}
