package pl.mkn.tdw.integrations.jira;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;
import pl.mkn.tdw.integrations.confluence.ConfluencePageContent;
import pl.mkn.tdw.integrations.confluence.ConfluencePagePort;

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
    private final ConfluencePagePort confluencePagePort;

    @Override
    public JiraIssueMaterial getIssueMaterial(String issueKey) {
        return getIssueMaterial(JiraIssueMaterialRequest.detailed(issueKey), null);
    }

    @Override
    public JiraIssueMaterial getIssueMaterial(JiraIssueMaterialRequest request) {
        return getIssueMaterial(request, null);
    }

    private JiraIssueMaterial getIssueMaterial(JiraIssueMaterialRequest request, String excludedSubTaskKey) {
        var issueKey = request.issueKey();

        try {
            var issue = fetchIssue(request);
            var remoteLinks = request.includeRemoteLinks() ? fetchRemoteLinks(issueKey) : List.<JiraIssueLink>of();
            return toMaterial(request, issue, remoteLinks, excludedSubTaskKey);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return missingIssue(issueKey);
            }
            throw new IllegalStateException("Jira issue material request failed for " + issueKey, exception);
        }
    }

    private JsonNode fetchIssue(JiraIssueMaterialRequest request) {
        return restClientFactory.create()
                .get()
                .uri(issueUri(request))
                .retrieve()
                .body(JsonNode.class);
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

    private JiraIssueMaterial toMaterial(
            JiraIssueMaterialRequest request,
            JsonNode issue,
            List<JiraIssueLink> remoteLinks,
            String excludedSubTaskKey
    ) {
        var issueKey = request.issueKey();
        var fields = issue != null ? issue.path("fields") : null;
        var links = new ArrayList<JiraIssueLink>();
        if (request.includeIssueLinks()) {
            links.addAll(issueLinks(fields != null ? fields.path("issuelinks") : null));
        }
        links.addAll(remoteLinks);

        var limitations = new ArrayList<String>();
        var parentIssue = request.includeParent() ? parentIssue(issueKey, fields, limitations) : null;
        var subTasks = request.includeSubTasks()
                ? subTasks(fields != null ? fields.path("subtasks") : null, limitations, excludedSubTaskKey)
                : List.<JiraIssueMaterial>of();
        var confluencePages = request.includeConfluencePages()
                ? confluencePages(remoteLinks, limitations)
                : List.<JiraConfluencePage>of();
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
                subTasks,
                parentIssue,
                confluencePages,
                request.includeComments()
                        ? comments(fields != null ? fields.path("comment").path("comments") : null)
                        : List.of(),
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
                null,
                List.of(),
                List.of(),
                List.of("Jira issue was not found or is not visible for configured credentials.")
        );
    }

    private JiraIssueMaterial parentIssue(String targetIssueKey, JsonNode fields, List<String> limitations) {
        if (fields == null || fields.isMissingNode() || !isSubTask(fields)) {
            return null;
        }

        var parentKey = text(fields.path("parent"), "key", "");
        if (!StringUtils.hasText(parentKey)) {
            limitations.add("Jira target issue is a subtask, but parent key was not available.");
            return null;
        }
        try {
            return getIssueMaterial(new JiraIssueMaterialRequest(
                    parentKey,
                    true,
                    true,
                    true,
                    true,
                    false,
                    true
            ), targetIssueKey);
        } catch (RuntimeException exception) {
            limitations.add("Jira parent issue " + parentKey + " could not be fetched: " + safeMessage(exception));
            return null;
        }
    }

    private boolean isSubTask(JsonNode fields) {
        if (fields.path("issuetype").path("subtask").asBoolean(false)) {
            return true;
        }
        var issueType = text(fields.path("issuetype"), "name", "");
        return StringUtils.hasText(issueType)
                && issueType.toLowerCase(java.util.Locale.ROOT).replace("-", "").replace(" ", "").contains("subtask");
    }

    private List<JiraIssueMaterial> subTasks(JsonNode subTasks, List<String> limitations, String excludedSubTaskKey) {
        if (subTasks == null || !subTasks.isArray()) {
            return List.of();
        }

        var values = new ArrayList<JiraIssueMaterial>();
        var maxSubTasks = Math.max(0, properties.getMaxSubTasks());
        for (var subTask : subTasks) {
            if (values.size() >= maxSubTasks) {
                limitations.add("Jira subtasks were truncated to analysis.jira.max-sub-tasks=" + maxSubTasks + ".");
                break;
            }
            var subTaskKey = text(subTask, "key", "");
            if (!StringUtils.hasText(subTaskKey)) {
                continue;
            }
            if (StringUtils.hasText(excludedSubTaskKey) && subTaskKey.equalsIgnoreCase(excludedSubTaskKey.trim())) {
                continue;
            }
            try {
                values.add(getIssueMaterial(new JiraIssueMaterialRequest(
                        subTaskKey,
                        true,
                        true,
                        true,
                        false,
                        false,
                        true
                ), null));
            } catch (RuntimeException exception) {
                limitations.add("Jira subtask " + subTaskKey + " could not be fetched: " + safeMessage(exception));
            }
        }
        return List.copyOf(values);
    }

    private List<JiraConfluencePage> confluencePages(List<JiraIssueLink> remoteLinks, List<String> limitations) {
        if (remoteLinks == null || remoteLinks.isEmpty()) {
            return List.of();
        }

        var values = new ArrayList<JiraConfluencePage>();
        for (var link : remoteLinks) {
            if (!"remote-link".equals(link.type()) || !StringUtils.hasText(link.url())) {
                continue;
            }
            confluencePagePort.getPageContent(link.url()).ifPresent(page -> {
                values.add(toJiraConfluencePage(page));
                limitations.addAll(page.limitations());
            });
        }
        return List.copyOf(values);
    }

    private JiraConfluencePage toJiraConfluencePage(ConfluencePageContent page) {
        return new JiraConfluencePage(
                page.pageId(),
                page.title(),
                page.url(),
                page.content(),
                page.version(),
                page.limitations()
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

    private URI issueUri(JiraIssueMaterialRequest request) {
        var fields = new LinkedHashSet<String>();
        fields.addAll(List.of("summary", "description", "status", "issuetype", "labels"));
        if (request.includeIssueLinks()) {
            fields.add("issuelinks");
        }
        if (request.includeSubTasks()) {
            fields.add("subtasks");
        }
        if (request.includeParent()) {
            fields.add("parent");
        }
        if (request.includeComments()) {
            fields.add("comment");
        }
        fields.addAll(properties.getAcceptanceCriteriaFieldIds());

        return URI.create(apiBaseUrl()
                + "/issue/" + encodePathSegment(request.issueKey())
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

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }
}
