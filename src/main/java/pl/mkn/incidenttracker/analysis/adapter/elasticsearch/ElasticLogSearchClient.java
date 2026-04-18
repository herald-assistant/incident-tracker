package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ElasticLogSearchClient {

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
