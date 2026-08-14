package pl.mkn.tdw.integrations.jira;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class JiraRestIssueSearchAdapter implements JiraIssueSearchPort, JiraIssueStatusHistoryPort {

    private static final List<String> SEARCH_FIELDS = List.of(
            "status",
            "statuscategorychangedate"
    );
    private static final DateTimeFormatter JIRA_OFFSET_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final JiraProperties properties;
    private final JiraRestClientFactory restClientFactory;
    private final Map<String, String> statusCategoryCache = new ConcurrentHashMap<>();

    @Override
    public JiraIssueSearchResult searchIssues(JiraIssueSearchRequest request) {
        var effectiveJql = jql(request);
        var issues = new ArrayList<JiraIssueSearchItem>();
        var limitations = new ArrayList<String>();
        var startAt = 0;
        var total = 0;

        try {
            while (issues.size() < request.maxIssues()) {
                var maxResults = Math.min(request.pageSize(), request.maxIssues() - issues.size());
                var response = restClientFactory.create()
                        .post()
                        .uri(searchUri())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(searchBody(effectiveJql, startAt, maxResults))
                        .retrieve()
                        .body(JsonNode.class);
                if (response == null || response.isNull()) {
                    limitations.add("Jira search returned an empty response.");
                    break;
                }

                total = response.path("total").asInt(total);
                var page = response.path("issues");
                if (!page.isArray() || page.isEmpty()) {
                    break;
                }
                for (var issue : page) {
                    issues.add(toSearchItem(issue));
                    if (issues.size() >= request.maxIssues()) {
                        break;
                    }
                }
                startAt += page.size();
                if (startAt >= total || page.size() < maxResults) {
                    break;
                }
            }
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Jira issue search failed with HTTP " + exception.getStatusCode().value(),
                    exception
            );
        }

        var truncated = total > issues.size();
        if (truncated) {
            limitations.add("Jira issue search was truncated to " + request.maxIssues() + " issues.");
        }
        return new JiraIssueSearchResult(
                effectiveJql,
                total,
                truncated,
                issues,
                limitations
        );
    }

    @Override
    public JiraIssueStatusHistory getStatusHistory(String issueKey) {
        if (!StringUtils.hasText(issueKey)) {
            throw new IllegalArgumentException("issueKey must not be blank");
        }
        var normalizedKey = issueKey.trim();
        var limitations = new ArrayList<String>();
        try {
            var response = restClientFactory.create()
                    .get()
                    .uri(issueChangelogUri(normalizedKey))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.isNull()) {
                return new JiraIssueStatusHistory(
                        normalizedKey,
                        false,
                        List.of(),
                        List.of("Jira status history returned an empty response.")
                );
            }

            var changelog = response.path("changelog");
            var histories = changelog.path("histories");
            var transitions = new ArrayList<JiraIssueStatusTransition>();
            var maxEntries = Math.max(1, properties.getMaxChangelogEntries());
            var processedHistories = 0;
            if (histories.isArray()) {
                for (var history : histories) {
                    if (processedHistories >= maxEntries) {
                        break;
                    }
                    processedHistories++;
                    var changedAt = parseInstant(text(history, "created"));
                    for (var item : history.path("items")) {
                        if (!"status".equalsIgnoreCase(text(item, "field"))) {
                            continue;
                        }
                        var toStatusId = text(item, "to");
                        transitions.add(new JiraIssueStatusTransition(
                                changedAt,
                                text(item, "from"),
                                text(item, "fromString"),
                                toStatusId,
                                text(item, "toString"),
                                statusCategory(toStatusId, limitations)
                        ));
                    }
                }
            }
            var total = changelog.path("total").asInt(histories.isArray() ? histories.size() : 0);
            var truncated = total > processedHistories;
            if (truncated) {
                limitations.add("Jira changelog was truncated; final Done transition cannot be guaranteed.");
            }
            return new JiraIssueStatusHistory(normalizedKey, truncated, transitions, limitations);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Jira status history request failed for " + normalizedKey
                            + " with HTTP " + exception.getStatusCode().value(),
                    exception
            );
        }
    }

    private Map<String, Object> searchBody(String jql, int startAt, int maxResults) {
        var body = new LinkedHashMap<String, Object>();
        body.put("jql", jql);
        body.put("startAt", startAt);
        body.put("maxResults", maxResults);
        body.put("fields", SEARCH_FIELDS);
        return body;
    }

    private JiraIssueSearchItem toSearchItem(JsonNode issue) {
        var fields = issue.path("fields");
        var status = fields.path("status");
        return new JiraIssueSearchItem(
                text(issue, "key"),
                text(status, "name"),
                firstText(status.path("statusCategory"), "key", "name"),
                parseInstant(text(fields, "statuscategorychangedate"))
        );
    }

    private String statusCategory(String statusId, List<String> limitations) {
        if (!StringUtils.hasText(statusId)) {
            return null;
        }
        var normalizedId = statusId.trim();
        var cached = statusCategoryCache.get(normalizedId);
        if (cached != null) {
            return cached;
        }
        if (statusCategoryCache.size() >= Math.max(1, properties.getMaxStatusLookups())) {
            limitations.add("Jira status category lookup limit was reached.");
            return null;
        }
        try {
            var response = restClientFactory.create()
                    .get()
                    .uri(statusUri(normalizedId))
                    .retrieve()
                    .body(JsonNode.class);
            var category = firstText(response != null ? response.path("statusCategory") : null, "key", "name");
            if (StringUtils.hasText(category)) {
                statusCategoryCache.put(normalizedId, category);
                return category;
            }
            limitations.add("Jira did not return status category for status id " + normalizedId + ".");
            return null;
        } catch (RestClientResponseException exception) {
            limitations.add("Jira status category lookup failed for status id " + normalizedId
                    + " with HTTP " + exception.getStatusCode().value() + ".");
            return null;
        }
    }

    private String jql(JiraIssueSearchRequest request) {
        return "project = \"" + request.projectKey() + "\""
                + " AND statusCategory = Done"
                + " AND statusCategoryChangedDate >= \"" + request.fromDate() + "\""
                + " AND statusCategoryChangedDate < \"" + request.toDateExclusive() + "\""
                + " ORDER BY key ASC";
    }

    private URI searchUri() {
        return URI.create(apiBaseUrl() + "/search");
    }

    private URI issueChangelogUri(String issueKey) {
        return URI.create(apiBaseUrl()
                + "/issue/" + encodePathSegment(issueKey)
                + "?fields=status&expand=changelog");
    }

    private URI statusUri(String statusId) {
        return URI.create(apiBaseUrl() + "/status/" + encodePathSegment(statusId));
    }

    private String apiBaseUrl() {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("analysis.jira.base-url must be configured for Jira REST mode.");
        }
        return properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl() + "rest/api/2"
                : properties.getBaseUrl() + "/rest/api/2";
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String firstText(JsonNode node, String first, String second) {
        var firstValue = text(node, first);
        return StringUtils.hasText(firstValue) ? firstValue : text(node, second);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        var value = node.path(fieldName);
        return value.isTextual() && StringUtils.hasText(value.asText()) ? value.asText().trim() : null;
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value.trim()).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return OffsetDateTime.parse(value.trim(), JIRA_OFFSET_MILLIS).toInstant();
                } catch (DateTimeParseException finalFailure) {
                    return null;
                }
            }
        }
    }
}
