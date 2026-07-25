package pl.mkn.tdw.integrations.jira;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JiraRestIssueAdapter implements JiraIssuePort {

    private final JiraProperties properties;
    private final JiraRestClientFactory restClientFactory;

    @Override
    public JiraIssueMaterial getIssueMaterial(String issueKey) {
        if (!StringUtils.hasText(issueKey)) {
            throw new IllegalArgumentException("issueKey must not be blank");
        }

        try {
            var issue = restClientFactory.create()
                    .get()
                    .uri(issueUri(issueKey.trim()))
                    .retrieve()
                    .body(JsonNode.class);
            var remoteLinks = fetchRemoteLinks(issueKey.trim());
            return toMaterial(issueKey.trim(), issue, remoteLinks);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return missingIssue(issueKey.trim());
            }
            throw new IllegalStateException("Jira issue material request failed for " + issueKey, exception);
        }
    }

    private List<JiraIssueLink> fetchRemoteLinks(String issueKey) {
        try {
            var links = restClientFactory.create()
                    .get()
                    .uri(remoteLinksUri(issueKey))
                    .retrieve()
                    .body(JsonNode.class);
            return remoteLinks(links);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return List.of();
            }
            return List.of(new JiraIssueLink(
                    "remote-link-fetch-failed",
                    "Jira remote links were not available: HTTP " + exception.getStatusCode().value(),
                    null
            ));
        }
    }

    private JiraIssueMaterial toMaterial(String issueKey, JsonNode issue, List<JiraIssueLink> remoteLinks) {
        var fields = issue != null ? issue.path("fields") : null;
        var links = new ArrayList<JiraIssueLink>();
        links.addAll(issueLinks(fields != null ? fields.path("issuelinks") : null));
        links.addAll(remoteLinks);

        var limitations = new ArrayList<String>();
        if (issue == null || issue.isMissingNode() || issue.isNull()) {
            limitations.add("Jira returned an empty issue body.");
        }
        if (remoteLinks.stream().anyMatch(link -> "remote-link-fetch-failed".equals(link.type()))) {
            limitations.add("Jira remote links could not be fetched.");
        }
        if (properties.getAcceptanceCriteriaFieldIds().isEmpty()) {
            limitations.add("Acceptance criteria fields are not configured under analysis.jira.acceptance-criteria-field-ids.");
        }

        return new JiraIssueMaterial(
                text(issue, "key", issueKey),
                issueBrowseUrl(issueKey),
                text(fields, "summary", ""),
                limitText(richText(fields != null ? fields.path("description") : null)),
                text(fields != null ? fields.path("issuetype") : null, "name", ""),
                text(fields != null ? fields.path("status") : null, "name", ""),
                textArray(fields != null ? fields.path("labels") : null),
                acceptanceCriteria(fields),
                links,
                comments(fields != null ? fields.path("comment").path("comments") : null),
                limitations
        );
    }

    private JiraIssueMaterial missingIssue(String issueKey) {
        return new JiraIssueMaterial(
                issueKey,
                issueBrowseUrl(issueKey),
                "",
                "",
                "",
                "NOT_FOUND",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Jira issue was not found or is not visible for configured credentials.")
        );
    }

    private List<String> acceptanceCriteria(JsonNode fields) {
        if (fields == null || fields.isMissingNode()) {
            return List.of();
        }

        var values = new ArrayList<String>();
        for (var fieldId : properties.getAcceptanceCriteriaFieldIds()) {
            if (!StringUtils.hasText(fieldId)) {
                continue;
            }
            var value = richText(fields.path(fieldId.trim()));
            if (StringUtils.hasText(value)) {
                values.add(limitText(value));
            }
        }
        return List.copyOf(values);
    }

    private List<JiraIssueComment> comments(JsonNode comments) {
        if (comments == null || !comments.isArray()) {
            return List.of();
        }

        var values = new ArrayList<JiraIssueComment>();
        var maxComments = Math.max(0, properties.getMaxComments());
        for (var comment : comments) {
            if (values.size() >= maxComments) {
                break;
            }
            values.add(new JiraIssueComment(
                    text(comment.path("author"), "displayName", ""),
                    text(comment, "created", ""),
                    limitText(richText(comment.path("body")))
            ));
        }
        return List.copyOf(values);
    }

    private List<JiraIssueLink> issueLinks(JsonNode issueLinks) {
        if (issueLinks == null || !issueLinks.isArray()) {
            return List.of();
        }

        var values = new ArrayList<JiraIssueLink>();
        for (var issueLink : issueLinks) {
            var linkedIssue = issueLink.hasNonNull("outwardIssue")
                    ? issueLink.path("outwardIssue")
                    : issueLink.path("inwardIssue");
            if (linkedIssue.isMissingNode() || linkedIssue.isNull()) {
                continue;
            }
            values.add(new JiraIssueLink(
                    text(issueLink.path("type"), "name", "issue-link"),
                    text(linkedIssue.path("fields"), "summary", text(linkedIssue, "key", "")),
                    issueBrowseUrl(text(linkedIssue, "key", ""))
            ));
        }
        return List.copyOf(values);
    }

    private List<JiraIssueLink> remoteLinks(JsonNode remoteLinks) {
        if (remoteLinks == null || !remoteLinks.isArray()) {
            return List.of();
        }

        var values = new ArrayList<JiraIssueLink>();
        var maxRemoteLinks = Math.max(0, properties.getMaxRemoteLinks());
        for (var link : remoteLinks) {
            if (values.size() >= maxRemoteLinks) {
                break;
            }
            var object = link.path("object");
            values.add(new JiraIssueLink(
                    "remote-link",
                    text(object, "title", text(object, "url", "")),
                    text(object, "url", "")
            ));
        }
        return List.copyOf(values);
    }

    private List<String> textArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        var values = new LinkedHashSet<String>();
        for (var item : node) {
            var value = item.isTextual() ? item.asText() : richText(item);
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
        return List.copyOf(values);
    }

    private String richText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            var parts = new ArrayList<String>();
            for (var child : node) {
                var text = richText(child);
                if (StringUtils.hasText(text)) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts);
        }
        if (node.isObject()) {
            if (node.hasNonNull("text")) {
                return node.path("text").asText();
            }
            if (node.has("content")) {
                return richText(node.path("content"));
            }
            if (node.hasNonNull("value")) {
                return richText(node.path("value"));
            }
        }
        return "";
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        var value = node.path(fieldName);
        return value.isTextual() && StringUtils.hasText(value.asText()) ? value.asText().trim() : fallback;
    }

    private String limitText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var trimmed = value.trim();
        var maxCharacters = Math.max(100, properties.getMaxTextCharacters());
        return trimmed.length() > maxCharacters ? trimmed.substring(0, maxCharacters) : trimmed;
    }

    private URI issueUri(String issueKey) {
        var fields = new LinkedHashSet<String>();
        fields.addAll(List.of("summary", "description", "status", "issuetype", "labels", "issuelinks", "comment"));
        fields.addAll(properties.getAcceptanceCriteriaFieldIds());

        return URI.create(apiBaseUrl()
                + "/issue/" + encodePathSegment(issueKey)
                + "?fields=" + encodeQueryParam(String.join(",", fields)));
    }

    private URI remoteLinksUri(String issueKey) {
        return URI.create(apiBaseUrl()
                + "/issue/" + encodePathSegment(issueKey)
                + "/remotelink");
    }

    private String issueBrowseUrl(String issueKey) {
        if (!StringUtils.hasText(issueKey) || !StringUtils.hasText(properties.getBaseUrl())) {
            return "";
        }
        var baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        return baseUrl + "/browse/" + issueKey.trim();
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

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }
}
