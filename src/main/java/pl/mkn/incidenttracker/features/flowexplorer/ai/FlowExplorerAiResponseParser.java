package pl.mkn.incidenttracker.features.flowexplorer.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class FlowExplorerAiResponseParser {

    private final ObjectMapper objectMapper;

    public FlowExplorerAiResponse parse(String assistantContent) {
        if (!StringUtils.hasText(assistantContent)) {
            return FlowExplorerAiResponse.parseFallback("AI response was empty.");
        }

        var node = parseJsonNode(assistantContent);
        if (node == null || !node.isObject()) {
            return FlowExplorerAiResponse.parseFallback("AI response was not valid JSON.");
        }

        return new FlowExplorerAiResponse(
                text(node, "userIntentSummary"),
                text(node, "audienceSummary"),
                endpointContract(node.get("endpointContract")),
                flowSteps(node.get("flowSteps")),
                textList(node.get("businessRules")),
                textList(node.get("validations")),
                textList(node.get("persistence")),
                textList(node.get("externalIntegrations")),
                textList(node.get("testScenarios")),
                textList(node.get("risksAndEdgeCases")),
                textList(node.get("openQuestions")),
                textList(node.get("visibilityLimits")),
                textList(node.get("sourceReferences")),
                confidence(text(node, "confidence"))
        );
    }

    private FlowExplorerAiEndpointContract endpointContract(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return new FlowExplorerAiEndpointContract(
                text(node, "method"),
                text(node, "path"),
                text(node, "purpose"),
                textList(node.get("request")),
                textList(node.get("response")),
                textList(node.get("parameters"))
        );
    }

    private List<FlowExplorerAiFlowStep> flowSteps(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        var steps = new ArrayList<FlowExplorerAiFlowStep>();
        for (var step : node) {
            if (!step.isObject()) {
                continue;
            }
            steps.add(new FlowExplorerAiFlowStep(
                    step.hasNonNull("order") ? step.get("order").asInt() : steps.size() + 1,
                    text(step, "title"),
                    text(step, "plainLanguage"),
                    text(step, "technicalGrounding"),
                    textList(step.get("sourceRefs"))
            ));
        }
        return List.copyOf(steps);
    }

    private List<String> textList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            var values = new ArrayList<String>();
            for (var item : node) {
                var value = text(item);
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            }
            return List.copyOf(values);
        }
        var value = text(node);
        return StringUtils.hasText(value) ? List.of(value) : List.of();
    }

    private String text(JsonNode objectNode, String fieldName) {
        if (objectNode == null || !objectNode.isObject()) {
            return null;
        }
        return text(objectNode.get(fieldName));
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return StringUtils.hasText(node.asText()) ? node.asText().trim() : null;
        }
        return node.toString();
    }

    private String confidence(String value) {
        if (!StringUtils.hasText(value)) {
            return "low";
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "high", "medium", "low" -> normalized;
            default -> "low";
        };
    }

    private JsonNode parseJsonNode(String assistantContent) {
        var trimmed = assistantContent.trim();
        var direct = readTree(trimmed);
        if (direct != null) {
            return direct;
        }

        var fencedJson = fencedJson(trimmed);
        if (fencedJson != null) {
            var fenced = readTree(fencedJson);
            if (fenced != null) {
                return fenced;
            }
        }

        var objectJson = firstObjectJson(trimmed);
        return objectJson != null ? readTree(objectJson) : null;
    }

    private JsonNode readTree(String candidate) {
        try {
            return objectMapper.readTree(candidate);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String fencedJson(String content) {
        var fenceStart = content.indexOf("```");
        if (fenceStart < 0) {
            return null;
        }
        var contentStart = content.indexOf('\n', fenceStart + 3);
        if (contentStart < 0) {
            return null;
        }
        var fenceEnd = content.indexOf("```", contentStart + 1);
        if (fenceEnd < 0) {
            return null;
        }
        var fenced = content.substring(contentStart + 1, fenceEnd).trim();
        return StringUtils.hasText(fenced) ? fenced : null;
    }

    private String firstObjectJson(String content) {
        var start = content.indexOf('{');
        if (start < 0) {
            return null;
        }

        var depth = 0;
        var inString = false;
        var escaped = false;
        for (var index = start; index < content.length(); index++) {
            var character = content.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (character == '{') {
                depth++;
            }
            if (character == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, index + 1);
                }
            }
        }
        return null;
    }
}
