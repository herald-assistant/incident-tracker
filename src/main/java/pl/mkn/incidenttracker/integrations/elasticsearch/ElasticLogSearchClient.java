package pl.mkn.incidenttracker.integrations.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ElasticLogSearchClient {

    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile("\\b(GET|POST|PUT|PATCH|DELETE|OPTIONS|HEAD)\\b");
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\b(?:status|HTTP)[:= ]+(\\d{3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_PATH_PATTERN = Pattern.compile("\\b(?:GET|POST|PUT|PATCH|DELETE|OPTIONS|HEAD)\\s+((?:https?://[^\\s\"']+)?/[^\\s\"']*)");
    private static final List<String> PATH_FIELDS = List.of(
            "fields.path",
            "fields.requestPath",
            "fields.requestUri",
            "fields.uri",
            "fields.url",
            "fields.http.path",
            "fields.http.target",
            "fields.http.url",
            "fields.http.request.path",
            "path",
            "requestPath",
            "requestUri",
            "uri",
            "url",
            "http.path",
            "http.target",
            "http.url",
            "http.request.path"
    );
    private static final List<String> METHOD_FIELDS = List.of(
            "fields.method",
            "fields.httpMethod",
            "fields.requestMethod",
            "fields.http.method",
            "fields.http.request.method",
            "method",
            "httpMethod",
            "requestMethod",
            "http.method",
            "http.request.method"
    );
    private static final List<String> STATUS_FIELDS = List.of(
            "fields.status",
            "fields.statusCode",
            "fields.httpStatus",
            "fields.responseStatus",
            "fields.http.status_code",
            "fields.http.response.status_code",
            "status",
            "statusCode",
            "httpStatus",
            "responseStatus",
            "http.status_code",
            "http.response.status_code"
    );

    private final ElasticRestClientFactory elasticRestClientFactory;

    public ElasticLogSearchResult search(ElasticConnectionDetails connectionDetails, ElasticLogSearchCriteria criteria) {
        validate(connectionDetails, criteria);

        var root = elasticRestClientFactory.create(connectionDetails)
                .post()
                .uri(consoleProxyUri(connectionDetails.baseUrl(), criteria.kibanaSpaceId(), criteria.indexPattern()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(searchRequestBody(criteria))
                .retrieve()
                .body(JsonNode.class);

        var entries = root != null ? mapEntries(root.path("hits").path("hits"), criteria) : List.<ElasticLogEntry>of();

        return new ElasticLogSearchResult(
                criteria.correlationId(),
                criteria.indexPattern(),
                criteria.size(),
                extractTotalHits(root),
                entries.size(),
                root != null ? root.path("took").asInt(0) : 0,
                root != null && root.path("timed_out").asBoolean(false),
                entries,
                entries.isEmpty()
                        ? "No Elasticsearch logs found for correlationId: " + criteria.correlationId()
                        : "OK"
        );
    }

    public ElasticHttpCallSummaryResult summarizeHttpCalls(
            ElasticConnectionDetails connectionDetails,
            ElasticHttpCallSummaryCriteria criteria
    ) {
        validate(connectionDetails, criteria);

        var root = elasticRestClientFactory.create(connectionDetails)
                .post()
                .uri(consoleProxyUri(connectionDetails.baseUrl(), criteria.kibanaSpaceId(), criteria.indexPattern()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(httpCallSummaryRequestBody(criteria))
                .retrieve()
                .body(JsonNode.class);

        var samples = root != null
                ? mapHttpCallSamples(root.path("hits").path("hits"), criteria.pathPattern(), criteria.sampleSize())
                : List.<ElasticHttpCallSample>of();

        return new ElasticHttpCallSummaryResult(
                criteria.pathPattern(),
                criteria.method(),
                criteria.serviceName(),
                criteria.timeWindowDays(),
                criteria.indexPattern(),
                criteria.sampleSize(),
                extractTotalHits(root),
                samples.size(),
                root != null ? root.path("took").asInt(0) : 0,
                root != null && root.path("timed_out").asBoolean(false),
                statusBuckets(samples),
                samples,
                samples.isEmpty()
                        ? "No Elasticsearch HTTP calls found for pathPattern: " + criteria.pathPattern()
                        : "OK"
        );
    }

    public ElasticHttpCallLogsResult fetchHttpCallLogs(
            ElasticConnectionDetails connectionDetails,
            ElasticHttpCallLogsCriteria criteria
    ) {
        validate(connectionDetails, criteria);

        var root = elasticRestClientFactory.create(connectionDetails)
                .post()
                .uri(consoleProxyUri(connectionDetails.baseUrl(), criteria.kibanaSpaceId(), criteria.indexPattern()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(httpCallLogsRequestBody(criteria))
                .retrieve()
                .body(JsonNode.class);

        var entries = root != null ? mapEntries(root.path("hits").path("hits"), logEntryCriteria(criteria)) : List.<ElasticLogEntry>of();

        return new ElasticHttpCallLogsResult(
                criteria.correlationId(),
                criteria.path(),
                criteria.status(),
                criteria.method(),
                criteria.timeWindowDays(),
                criteria.detailLevel(),
                criteria.indexPattern(),
                criteria.size(),
                extractTotalHits(root),
                entries.size(),
                root != null ? root.path("took").asInt(0) : 0,
                root != null && root.path("timed_out").asBoolean(false),
                entries,
                entries.isEmpty()
                        ? "No Elasticsearch HTTP call logs found for the provided criteria."
                        : "OK"
        );
    }

    private void validate(ElasticConnectionDetails connectionDetails, ElasticLogSearchCriteria criteria) {
        if (!StringUtils.hasText(connectionDetails.baseUrl())) {
            throw new IllegalStateException("Elasticsearch base URL must be configured.");
        }
        if (!StringUtils.hasText(criteria.kibanaSpaceId())) {
            throw new IllegalStateException("Elasticsearch Kibana space id must be configured.");
        }
        if (!StringUtils.hasText(criteria.indexPattern())) {
            throw new IllegalStateException("Elasticsearch index pattern must be configured.");
        }
        if (!StringUtils.hasText(criteria.correlationId())) {
            throw new IllegalStateException("correlationId must not be blank");
        }
    }

    private void validate(ElasticConnectionDetails connectionDetails, ElasticHttpCallSummaryCriteria criteria) {
        validateConnectionAndIndex(connectionDetails, criteria.kibanaSpaceId(), criteria.indexPattern());
        if (!StringUtils.hasText(criteria.pathPattern())) {
            throw new IllegalStateException("pathPattern must not be blank");
        }
    }

    private void validate(ElasticConnectionDetails connectionDetails, ElasticHttpCallLogsCriteria criteria) {
        validateConnectionAndIndex(connectionDetails, criteria.kibanaSpaceId(), criteria.indexPattern());
        if (!StringUtils.hasText(criteria.correlationId()) && !StringUtils.hasText(criteria.path())) {
            throw new IllegalStateException("correlationId or path must be provided");
        }
    }

    private void validateConnectionAndIndex(
            ElasticConnectionDetails connectionDetails,
            String kibanaSpaceId,
            String indexPattern
    ) {
        if (!StringUtils.hasText(connectionDetails.baseUrl())) {
            throw new IllegalStateException("Elasticsearch base URL must be configured.");
        }
        if (!StringUtils.hasText(kibanaSpaceId)) {
            throw new IllegalStateException("Elasticsearch Kibana space id must be configured.");
        }
        if (!StringUtils.hasText(indexPattern)) {
            throw new IllegalStateException("Elasticsearch index pattern must be configured.");
        }
    }

    private URI consoleProxyUri(String baseUrl, String kibanaSpaceId, String indexPattern) {
        return URI.create(normalizeBaseUrl(baseUrl)
                + "/s/" + encodePathSegment(kibanaSpaceId)
                + "/api/console/proxy?path=" + encodeQueryParam(indexPattern + "/_search")
                + "&method=GET");
    }

    private Object searchRequestBody(ElasticLogSearchCriteria criteria) {
        var term = new LinkedHashMap<String, Object>();
        term.put("fields.correlationId", criteria.correlationId());

        return Map.of(
                "size", criteria.size(),
                "sort", List.of(Map.of("@timestamp", "asc")),
                "query", Map.of(
                        "bool", Map.of(
                                "filter", List.of(Map.of("term", term))
                        )
                )
        );
    }

    private Object httpCallSummaryRequestBody(ElasticHttpCallSummaryCriteria criteria) {
        var filters = new ArrayList<Object>();
        filters.add(timeWindowFilter(criteria.timeWindowDays()));
        filters.add(pathContainsFilter(criteria.pathPattern()));

        if (StringUtils.hasText(criteria.method())) {
            filters.add(methodFilter(criteria.method()));
        }
        if (StringUtils.hasText(criteria.serviceName())) {
            var term = new LinkedHashMap<String, Object>();
            term.put("fields.microservice", criteria.serviceName().trim());
            filters.add(Map.of("term", term));
        }

        return Map.of(
                "size", criteria.sampleSize(),
                "sort", List.of(Map.of("@timestamp", "desc")),
                "query", Map.of(
                        "bool", Map.of(
                                "filter", filters
                        )
                )
        );
    }

    private Object httpCallLogsRequestBody(ElasticHttpCallLogsCriteria criteria) {
        var filters = new ArrayList<Object>();
        filters.add(timeWindowFilter(criteria.timeWindowDays()));

        if (StringUtils.hasText(criteria.correlationId())) {
            var term = new LinkedHashMap<String, Object>();
            term.put("fields.correlationId", criteria.correlationId().trim());
            filters.add(Map.of("term", term));
        }
        if (StringUtils.hasText(criteria.path())) {
            filters.add(pathContainsFilter(criteria.path()));
        }
        if (criteria.status() != null) {
            filters.add(statusFilter(criteria.status()));
        }
        if (StringUtils.hasText(criteria.method())) {
            filters.add(methodFilter(criteria.method()));
        }

        return Map.of(
                "size", criteria.size(),
                "sort", List.of(Map.of("@timestamp", "asc")),
                "query", Map.of(
                        "bool", Map.of(
                                "filter", filters
                        )
                )
        );
    }

    private Object timeWindowFilter(int timeWindowDays) {
        return Map.of(
                "range", Map.of(
                        "@timestamp", Map.of(
                                "gte", "now-" + timeWindowDays + "d",
                                "lte", "now"
                        )
                )
        );
    }

    private Object pathContainsFilter(String pathPattern) {
        var value = "*" + pathPattern.trim() + "*";
        var should = new ArrayList<Object>();
        for (var field : PATH_FIELDS) {
            should.add(Map.of("wildcard", Map.of(field, value)));
        }
        should.add(Map.of("query_string", Map.of(
                "fields", List.of("fields.message"),
                "query", quotedQueryString(pathPattern)
        )));

        return Map.of(
                "bool", Map.of(
                        "should", should,
                        "minimum_should_match", 1
                )
        );
    }

    private String quotedQueryString(String value) {
        var escaped = value.trim()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private Object methodFilter(String method) {
        var normalized = method.trim().toUpperCase();
        var should = new ArrayList<Object>();
        for (var field : METHOD_FIELDS) {
            should.add(Map.of("term", Map.of(field, normalized)));
        }
        should.add(Map.of("wildcard", Map.of("fields.message", "*" + normalized + "*")));

        return Map.of(
                "bool", Map.of(
                        "should", should,
                        "minimum_should_match", 1
                )
        );
    }

    private Object statusFilter(Integer status) {
        var should = new ArrayList<Object>();
        for (var field : STATUS_FIELDS) {
            should.add(Map.of("term", Map.of(field, status)));
            should.add(Map.of("term", Map.of(field, String.valueOf(status))));
        }
        should.add(Map.of("wildcard", Map.of("fields.message", "*" + status + "*")));

        return Map.of(
                "bool", Map.of(
                        "should", should,
                        "minimum_should_match", 1
                )
        );
    }

    private List<ElasticLogEntry> mapEntries(JsonNode hitsNode, ElasticLogSearchCriteria criteria) {
        if (hitsNode == null || !hitsNode.isArray()) {
            return List.of();
        }

        var entries = new ArrayList<ElasticLogEntry>();

        for (var hit : hitsNode) {
            var source = hit.path("_source");
            var fields = source.path("fields");
            var kubernetes = source.path("kubernetes");
            var container = source.path("container");
            var message = truncate(rawText(fields, "message"), criteria.maxMessageCharacters());
            var exception = truncate(rawText(fields, "exception"), criteria.maxExceptionCharacters());

            entries.add(new ElasticLogEntry(
                    text(source, "@timestamp"),
                    text(fields, "type"),
                    text(fields, "microservice"),
                    text(fields, "class"),
                    message.value(),
                    exception.value(),
                    text(fields, "thread"),
                    text(fields, "spanId"),
                    text(kubernetes, "namespace"),
                    text(kubernetes.path("pod"), "name"),
                    text(kubernetes.path("container"), "name"),
                    text(container.path("image"), "name"),
                    text(hit, "_index"),
                    text(hit, "_id"),
                    message.truncated(),
                    exception.truncated()
            ));
        }

        return List.copyOf(entries);
    }

    private List<ElasticHttpCallSample> mapHttpCallSamples(JsonNode hitsNode, String pathPattern, int sampleSize) {
        if (hitsNode == null || !hitsNode.isArray()) {
            return List.of();
        }

        var samples = new ArrayList<ElasticHttpCallSample>();

        for (var hit : hitsNode) {
            var source = hit.path("_source");
            var fields = source.path("fields");
            var message = rawText(fields, "message");
            var path = firstText(source, fields, PATH_FIELDS);

            if (!StringUtils.hasText(path)) {
                path = extractPath(message);
            }

            samples.add(new ElasticHttpCallSample(
                    text(source, "@timestamp"),
                    firstNonBlank(firstText(source, fields, METHOD_FIELDS), extractMethod(message)),
                    path,
                    firstNonNull(parseStatus(firstText(source, fields, STATUS_FIELDS)), extractStatus(message)),
                    text(fields, "correlationId"),
                    text(fields, "microservice"),
                    text(fields, "class"),
                    truncate(message, 500).value(),
                    text(hit, "_index"),
                    text(hit, "_id")
            ));
        }

        return samples.stream()
                .filter(sample -> matchesPathPattern(sample.path(), sample.message(), pathPattern))
                .limit(sampleSize)
                .toList();
    }

    private List<ElasticHttpStatusBucket> statusBuckets(List<ElasticHttpCallSample> samples) {
        var counts = new LinkedHashMap<String, Long>();
        for (var sample : samples) {
            var key = sample.status() != null ? String.valueOf(sample.status()) : "unknown";
            counts.put(key, counts.getOrDefault(key, 0L) + 1);
        }

        return counts.entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> new ElasticHttpStatusBucket(entry.getKey(), entry.getValue()))
                .toList();
    }

    private ElasticLogSearchCriteria logEntryCriteria(ElasticHttpCallLogsCriteria criteria) {
        return new ElasticLogSearchCriteria(
                criteria.kibanaSpaceId(),
                criteria.indexPattern(),
                StringUtils.hasText(criteria.correlationId()) ? criteria.correlationId() : "<http-call-filter>",
                criteria.size(),
                criteria.maxMessageCharacters(),
                criteria.maxExceptionCharacters()
        );
    }

    private long extractTotalHits(JsonNode root) {
        if (root == null) {
            return 0;
        }

        var totalNode = root.path("hits").path("total");
        if (totalNode.isObject()) {
            return totalNode.path("value").asLong(0);
        }

        return totalNode.asLong(0);
    }

    private TruncatedValue truncate(String value, int maxCharacters) {
        if (!StringUtils.hasText(value)) {
            return new TruncatedValue(null, false);
        }

        var safeLimit = maxCharacters > 0 ? maxCharacters : value.length();
        if (value.length() <= safeLimit) {
            return new TruncatedValue(value, false);
        }

        return new TruncatedValue(value.substring(0, safeLimit), true);
    }

    private String rawText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }

        var value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private String text(JsonNode node, String fieldName) {
        var value = rawText(node, fieldName);
        return StringUtils.hasText(value) ? value : null;
    }

    private String firstText(JsonNode source, JsonNode fields, List<String> fieldNames) {
        for (var fieldName : fieldNames) {
            var value = textByPath(fieldName.startsWith("fields.") ? fields : source, stripFieldsPrefix(fieldName));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String textByPath(JsonNode node, String path) {
        if (node == null || !StringUtils.hasText(path)) {
            return null;
        }

        var direct = rawText(node, path);
        if (StringUtils.hasText(direct)) {
            return direct;
        }

        var current = node;
        for (var part : path.split("\\.")) {
            current = current.path(part);
            if (current.isMissingNode() || current.isNull()) {
                return null;
            }
        }

        return current.isValueNode() ? current.asText() : null;
    }

    private String stripFieldsPrefix(String fieldName) {
        return fieldName.startsWith("fields.") ? fieldName.substring("fields.".length()) : fieldName;
    }

    private String extractMethod(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        var matcher = HTTP_METHOD_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Integer extractStatus(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        var matcher = HTTP_STATUS_PATTERN.matcher(message);
        return matcher.find() ? parseStatus(matcher.group(1)) : null;
    }

    private String extractPath(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        var matcher = HTTP_PATH_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Integer parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean matchesPathPattern(String path, String message, String pathPattern) {
        if (!StringUtils.hasText(pathPattern)) {
            return true;
        }
        var expected = pathPattern.trim();
        return (StringUtils.hasText(path) && path.contains(expected))
                || (StringUtils.hasText(message) && message.contains(expected));
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private String normalizeBaseUrl(String baseUrl) {
        var normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private record TruncatedValue(
            String value,
            boolean truncated
    ) {
    }

}
