package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ToolJsonPayloadReader {

    private static final int MAX_SUMMARY_VALUES = 8;

    private final ObjectMapper objectMapper;

    JsonNode readJsonNode(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return null;
        }

        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse tool payload as JSON. reason={}", exception.getMessage());
            return null;
        }
    }

    String prettyPayload(JsonNode payload, String rawPayload, String fallback) {
        if (payload == null || payload.isNull()) {
            return StringUtils.hasText(rawPayload) ? rawPayload.trim() : fallback;
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to pretty print tool payload. reason={}", exception.getMessage());
            return StringUtils.hasText(rawPayload) ? rawPayload.trim() : fallback;
        }
    }

    String readTopLevelText(JsonNode payload, String fieldName) {
        if (payload == null || !payload.isObject()) {
            return null;
        }

        var field = payload.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }

        if (field.isValueNode()) {
            return field.asText();
        }

        return prettyPayload(field, field.toString(), null);
    }

    JsonNode field(JsonNode payload, String fieldName) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        return payload.get(fieldName);
    }

    List<JsonNode> iterableArray(JsonNode payload) {
        if (payload == null || !payload.isArray()) {
            return List.of();
        }

        var values = new ArrayList<JsonNode>();
        payload.forEach(values::add);
        return values;
    }

    String arraySizeText(JsonNode payload) {
        return payload != null && payload.isArray() ? String.valueOf(payload.size()) : null;
    }

    int arraySize(JsonNode payload) {
        return payload != null && payload.isArray() ? payload.size() : 0;
    }

    List<String> fieldNames(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return List.of();
        }

        var values = new ArrayList<String>();
        var fieldNames = payload.fieldNames();
        while (fieldNames.hasNext()) {
            values.add(fieldNames.next());
        }
        return values;
    }

    String firstArrayValue(JsonNode payload) {
        for (var item : iterableArray(payload)) {
            var value = nodeText(item);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    String summarizeJsonArray(JsonNode payload) {
        var values = new ArrayList<String>();
        for (var item : iterableArray(payload)) {
            addDistinct(values, nodeText(item));
        }
        return summarizeValues(values);
    }

    String summarizeValues(List<String> values) {
        var safeValues = values != null ? values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList() : List.<String>of();
        if (safeValues.isEmpty()) {
            return null;
        }

        var limit = Math.min(safeValues.size(), MAX_SUMMARY_VALUES);
        var preview = new ArrayList<>(safeValues.subList(0, limit));
        if (safeValues.size() > limit) {
            preview.add("... (" + safeValues.size() + " items)");
        }
        return String.join(", ", preview);
    }

    String joinNonBlank(String... values) {
        var parts = new ArrayList<String>();
        for (var value : values) {
            if (StringUtils.hasText(value) && !value.endsWith("=null")) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    String nodeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private void addDistinct(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value.trim())) {
            values.add(value.trim());
        }
    }
}
