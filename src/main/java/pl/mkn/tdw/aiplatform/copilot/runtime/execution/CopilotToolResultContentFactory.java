package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.shared.ai.AnalysisAiToolResultContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CopilotToolResultContentFactory {

    static final int MAX_RESULT_CHARACTERS = 12_000;
    static final int MAX_TEXT_LINES = 240;
    static final int MAX_ARRAY_ITEMS = 40;
    static final int MAX_OBJECT_FIELDS = 80;
    static final int MAX_STRING_CHARACTERS = 4_000;
    static final int MAX_JSON_DEPTH = 12;

    private final ObjectMapper objectMapper;

    public AnalysisAiToolResultContent capture(String rawContent) {
        if (rawContent == null) {
            return null;
        }
        try {
            return captureJson(rawContent, objectMapper.readTree(rawContent));
        } catch (JsonProcessingException exception) {
            return captureText(rawContent);
        }
    }

    private AnalysisAiToolResultContent captureJson(String rawContent, JsonNode source) {
        var budget = new JsonBudget(MAX_RESULT_CHARACTERS);
        var limited = limitJson(source, budget, 0);
        if (limited == null) {
            limited = JsonNodeFactory.instance.nullNode();
        }
        var value = objectMapper.convertValue(limited, Object.class);
        var retainedCharacters = jsonLength(limited);
        return new AnalysisAiToolResultContent(
                AnalysisAiToolResultContent.Format.JSON,
                value,
                budget.truncated(),
                rawContent.length(),
                retainedCharacters,
                budget.omittedEntries,
                budget.truncatedStrings
        );
    }

    private JsonNode limitJson(JsonNode source, JsonBudget budget, int depth) {
        if (source == null || source.isNull()) {
            return budget.consume(4) ? JsonNodeFactory.instance.nullNode() : null;
        }
        if (depth >= MAX_JSON_DEPTH && source.isContainerNode()) {
            budget.omittedEntries++;
            return null;
        }
        if (source.isObject()) {
            return limitObject(source, budget, depth);
        }
        if (source.isArray()) {
            return limitArray(source, budget, depth);
        }
        if (source.isTextual()) {
            return limitTextNode(source.textValue(), budget);
        }
        var serializedLength = jsonLength(source);
        return budget.consume(serializedLength) ? source.deepCopy() : null;
    }

    private ObjectNode limitObject(JsonNode source, JsonBudget budget, int depth) {
        if (!budget.consume(2)) {
            budget.omittedEntries += source.size();
            return null;
        }
        var result = JsonNodeFactory.instance.objectNode();
        var fields = new ArrayList<Map.Entry<String, JsonNode>>();
        source.fields().forEachRemaining(fields::add);
        fields.sort(Comparator
                .comparingInt((Map.Entry<String, JsonNode> field) -> retentionPriority(field.getValue()))
                .thenComparingInt(field -> retentionSize(field.getValue())));
        var accepted = 0;
        for (var field : fields) {
            if (accepted >= MAX_OBJECT_FIELDS) {
                budget.omittedEntries++;
                continue;
            }
            var fieldCost = jsonStringLength(field.getKey()) + 1 + (accepted > 0 ? 1 : 0);
            if (!budget.consume(fieldCost)) {
                budget.omittedEntries++;
                continue;
            }
            var value = limitJson(field.getValue(), budget, depth + 1);
            if (value == null) {
                budget.omittedEntries++;
                continue;
            }
            result.set(field.getKey(), value);
            accepted++;
        }
        return result;
    }

    private int retentionPriority(JsonNode value) {
        if (value == null || value.isNull() || value.isBoolean() || value.isNumber()) {
            return 0;
        }
        if (value.isTextual()) {
            return 1;
        }
        return 2;
    }

    private int retentionSize(JsonNode value) {
        return value != null && value.isTextual() ? jsonStringLength(value.textValue()) : 0;
    }

    private ArrayNode limitArray(JsonNode source, JsonBudget budget, int depth) {
        if (!budget.consume(2)) {
            budget.omittedEntries += source.size();
            return null;
        }
        var result = JsonNodeFactory.instance.arrayNode();
        for (var index = 0; index < source.size(); index++) {
            if (index >= MAX_ARRAY_ITEMS) {
                budget.omittedEntries++;
                continue;
            }
            if (index > 0 && !budget.consume(1)) {
                budget.omittedEntries += source.size() - index;
                break;
            }
            var value = limitJson(source.get(index), budget, depth + 1);
            if (value == null) {
                budget.omittedEntries++;
                continue;
            }
            result.add(value);
        }
        return result;
    }

    private JsonNode limitTextNode(String value, JsonBudget budget) {
        var source = value != null ? value : "";
        var maximumLength = Math.min(source.length(), MAX_STRING_CHARACTERS);
        var acceptedLength = longestJsonStringPrefix(source, maximumLength, budget.remaining);
        if (acceptedLength < source.length()) {
            budget.truncatedStrings++;
        }
        var limited = source.substring(0, acceptedLength);
        if (!budget.consume(jsonStringLength(limited))) {
            return null;
        }
        return JsonNodeFactory.instance.textNode(limited);
    }

    private int longestJsonStringPrefix(String source, int maximumLength, int availableCharacters) {
        var low = 0;
        var high = maximumLength;
        while (low < high) {
            var middle = (low + high + 1) / 2;
            if (jsonStringLength(source.substring(0, middle)) <= availableCharacters) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private AnalysisAiToolResultContent captureText(String rawContent) {
        var retainedLines = new ArrayList<String>();
        var sourceLines = splitLines(rawContent);
        var retainedCharacters = 0;
        var omittedLines = 0;
        for (var index = 0; index < sourceLines.size(); index++) {
            var line = sourceLines.get(index);
            if (index >= MAX_TEXT_LINES || retainedCharacters + line.length() > MAX_RESULT_CHARACTERS) {
                omittedLines = sourceLines.size() - index;
                break;
            }
            retainedLines.add(line);
            retainedCharacters += line.length();
        }
        if (retainedLines.isEmpty() && !rawContent.isEmpty()) {
            var retained = rawContent.substring(0, Math.min(rawContent.length(), MAX_RESULT_CHARACTERS));
            retainedLines.add(retained);
            retainedCharacters = retained.length();
            omittedLines = rawContent.length() > retainedCharacters ? 1 : 0;
        }
        var value = String.join("", retainedLines);
        var truncated = value.length() < rawContent.length();
        return new AnalysisAiToolResultContent(
                AnalysisAiToolResultContent.Format.TEXT,
                value,
                truncated,
                rawContent.length(),
                value.length(),
                omittedLines,
                0
        );
    }

    private List<String> splitLines(String value) {
        var lines = new ArrayList<String>();
        var start = 0;
        for (var index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\n') {
                lines.add(value.substring(start, index + 1));
                start = index + 1;
            }
        }
        if (start < value.length() || value.isEmpty()) {
            lines.add(value.substring(start));
        }
        return List.copyOf(lines);
    }

    private int jsonLength(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value).length();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize captured tool result content", exception);
        }
    }

    private int jsonStringLength(String value) {
        try {
            return objectMapper.writeValueAsString(value).length();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize captured tool result text", exception);
        }
    }

    private static final class JsonBudget {
        private int remaining;
        private int omittedEntries;
        private int truncatedStrings;

        private JsonBudget(int remaining) {
            this.remaining = remaining;
        }

        private boolean consume(int characters) {
            if (characters < 0 || characters > remaining) {
                return false;
            }
            remaining -= characters;
            return true;
        }

        private boolean truncated() {
            return omittedEntries > 0 || truncatedStrings > 0;
        }
    }
}
