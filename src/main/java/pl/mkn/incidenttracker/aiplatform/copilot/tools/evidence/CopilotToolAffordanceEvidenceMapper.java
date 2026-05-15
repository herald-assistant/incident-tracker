package pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CopilotToolAffordanceEvidenceMapper {

    static final String PROVIDER = "ai";
    static final String CATEGORY = "tool-affordances";

    private static final String ORDER_NAMESPACE = "tool-affordances";
    private static final String FALLBACK_KEY = "tool-affordance";
    private static final int MAX_ATTRIBUTE_LENGTH = 4_000;
    private static final int MAX_LINKS = 6;

    private final ObjectMapper objectMapper;

    AnalysisEvidenceSection capture(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        var resultNode = readJson(rawResult);
        if (resultNode == null || !resultNode.isObject()) {
            return null;
        }

        var affordances = resultNode.get("affordances");
        if (affordances == null || !affordances.isObject()) {
            return null;
        }

        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "toolCallId", toolCallId);
        addAttribute(attributes, "toolArguments", compactJson(readJson(rawArguments), rawArguments));
        addAttribute(attributes, "profile", text(affordances, "profile"));
        addAttribute(attributes, "resultSummary", resultSummary(resultNode));
        addAttribute(attributes, "availableExpansions", compactJson(affordances.get("availableExpansions"), ""));
        addAttribute(attributes, "suggestedNextReads", compactJson(affordances.get("suggestedNextReads"), ""));
        addAttribute(attributes, "suggestedTools", compactJson(affordances.get("suggestedTools"), ""));
        addAttribute(attributes, "reasonToExpand", text(affordances, "reasonToExpand"));
        addAttribute(attributes, "omittedBecause", compactJson(affordances.get("omittedBecause"), ""));
        addAttribute(attributes, "limitations", compactJson(affordances.get("limitations"), ""));
        addAttribute(attributes, "links", compactJson(limitedLinks(affordances.get("links")), ""));

        var truncation = affordances.get("truncation");
        if (truncation != null && truncation.isObject()) {
            addAttribute(attributes, "truncationTruncated", text(truncation, "truncated"));
            addAttribute(attributes, "truncationReason", text(truncation, "reason"));
            addAttribute(attributes, "truncationReturnedCounts", compactJson(truncation.get("returnedCounts"), ""));
            addAttribute(attributes, "truncationOmittedCounts", compactJson(truncation.get("omittedCounts"), ""));
        }

        return sessionEvidence.appendItem(
                PROVIDER,
                CATEGORY,
                key(toolCallId, toolName),
                ORDER_NAMESPACE,
                FALLBACK_KEY,
                new AnalysisEvidenceItem(
                        "Tool affordances: " + textOrFallback(toolName, "unknown tool"),
                        List.copyOf(attributes)
                )
        );
    }

    private JsonNode readJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String resultSummary(JsonNode resultNode) {
        var summary = objectMapper.createObjectNode();
        copyIfPresent(resultNode, summary, "type");
        copyIfPresent(resultNode, summary, "id");
        copyIfPresent(resultNode, summary, "label");
        copyIfPresent(resultNode, summary, "query");
        copyIfPresent(resultNode, summary, "limit");
        copyIfPresent(resultNode, summary, "totalItems");
        copyIfPresent(resultNode, summary, "totalPages");
        copyIfPresent(resultNode, summary, "totalResults");
        copyIfPresent(resultNode, summary, "truncated");
        addCount(resultNode, summary, "items", "itemCount");
        addCount(resultNode, summary, "results", "resultCount");
        addCount(resultNode, summary, "openQuestions", "openQuestionCount");
        addCount(resultNode, summary, "sourceRefs", "sourceRefCount");
        return compactJson(summary, "");
    }

    private void copyIfPresent(JsonNode source, com.fasterxml.jackson.databind.node.ObjectNode target, String fieldName) {
        var value = source.get(fieldName);
        if (value != null && !value.isNull()) {
            target.set(fieldName, value);
        }
    }

    private void addCount(JsonNode source, com.fasterxml.jackson.databind.node.ObjectNode target, String fieldName, String targetName) {
        var value = source.get(fieldName);
        if (value != null && value.isArray()) {
            target.put(targetName, value.size());
        }
    }

    private JsonNode limitedLinks(JsonNode links) {
        if (links == null || !links.isArray() || links.size() <= MAX_LINKS) {
            return links;
        }
        var limited = objectMapper.createArrayNode();
        for (var index = 0; index < Math.min(MAX_LINKS, links.size()); index++) {
            limited.add(links.get(index));
        }
        return limited;
    }

    private String compactJson(JsonNode node, String fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return clean(fallback);
        }
        try {
            return truncate(objectMapper.writeValueAsString(node), MAX_ATTRIBUTE_LENGTH);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to render tool affordance evidence attribute. reason={}", exception.getMessage());
            return "";
        }
    }

    private String text(JsonNode node, String fieldName) {
        var value = node != null ? node.get(fieldName) : null;
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return clean(value.asText());
        }
        return clean(value.toString());
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, truncate(value, MAX_ATTRIBUTE_LENGTH)));
        }
    }

    private String key(String toolCallId, String toolName) {
        return StringUtils.hasText(toolCallId) ? toolCallId.trim() : textOrFallback(toolName, FALLBACK_KEY);
    }

    private String textOrFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String truncate(String value, int maxLength) {
        var cleaned = clean(value);
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }
}
