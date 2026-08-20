package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DeliveryAiResponseParser {

    private static final Set<String> CLASSIFICATIONS =
            Set.of("DELIVERY", "EXCLUDED", "INSUFFICIENT_EVIDENCE");

    private final ObjectMapper objectMapper;

    public DeliveryAiResponse parse(String content) {
        var json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException("AI response did not contain JSON assessment.");
        }
        try {
            var root = objectMapper.readTree(json);
            return validatedResponse(
                    text(root, "classification"),
                    root.path("dimensions"),
                    root.path("confidence").asDouble(-1),
                    optionalTextList(root.path("evidenceSummary")),
                    optionalTextList(root.path("qualityFlags")),
                    optionalTextList(root.path("visibilityLimits"))
            );
        } catch (JsonProcessingException exception) {
            return recoverEssentialResponse(json, exception);
        }
    }

    private DeliveryAiResponse recoverEssentialResponse(String json, JsonProcessingException originalFailure) {
        String classification = null;
        JsonNode dimensionValues = null;
        Double confidence = null;
        try (JsonParser parser = objectMapper.getFactory().createParser(json)) {
            while (parser.nextToken() != null && !essentialFieldsComplete(classification, dimensionValues, confidence)) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    continue;
                }
                var fieldName = parser.currentName();
                var valueToken = parser.nextToken();
                if ("classification".equals(fieldName) && valueToken == JsonToken.VALUE_STRING) {
                    classification = parser.getValueAsString();
                } else if ("dimensions".equals(fieldName) && valueToken == JsonToken.START_OBJECT) {
                    dimensionValues = objectMapper.readTree(parser);
                } else if ("confidence".equals(fieldName) && valueToken != null && valueToken.isScalarValue()) {
                    confidence = parser.getValueAsDouble(-1);
                } else {
                    parser.skipChildren();
                }
            }
        } catch (IOException recoveryFailure) {
            if (!essentialFieldsComplete(classification, dimensionValues, confidence)) {
                throw unparsableResponse(originalFailure);
            }
        }
        if (!essentialFieldsComplete(classification, dimensionValues, confidence)) {
            throw unparsableResponse(originalFailure);
        }
        return validatedResponse(
                classification,
                dimensionValues,
                confidence,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private boolean essentialFieldsComplete(String classification, JsonNode dimensions, Double confidence) {
        if (!StringUtils.hasText(classification) || confidence == null) {
            return false;
        }
        return !"DELIVERY".equals(normalized(classification)) || dimensions != null;
    }

    private DeliveryAiResponse validatedResponse(
            String classificationValue,
            JsonNode dimensionValues,
            double confidence,
            List<String> evidenceSummary,
            List<String> qualityFlags,
            List<String> visibilityLimits
    ) {
        var classification = normalized(classificationValue);
        if (!CLASSIFICATIONS.contains(classification)) {
            throw new IllegalArgumentException("AI response classification is unsupported.");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("AI response confidence must be between 0 and 1.");
        }
        var dimensions = "DELIVERY".equals(classification) ? dimensions(dimensionValues) : null;
        return new DeliveryAiResponse(
                classification,
                dimensions,
                confidence,
                evidenceSummary,
                qualityFlags,
                visibilityLimits
        );
    }

    private IllegalArgumentException unparsableResponse(JsonProcessingException failure) {
        return new IllegalArgumentException("AI response JSON could not be parsed.", failure);
    }

    private DeliveryAssessmentDimensions dimensions(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("AI response did not contain dimensions.");
        }
        return new DeliveryAssessmentDimensions(
                requiredDimension(node, "outcomeBreadth"),
                requiredDimension(node, "domainDecisionComplexity"),
                requiredDimension(node, "applicationFlowComplexity"),
                requiredDimension(node, "boundaryAndDataComplexity"),
                requiredDimension(node, "verificationStateSpace"),
                requiredDimension(node, "implementedCompatibilityScope"),
                requiredDimension(node, "parameterizationComplexity")
        );
    }

    private int requiredDimension(JsonNode node, String name) {
        var value = node.get(name);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException("AI response dimension " + name + " is missing.");
        }
        return value.asInt();
    }

    private List<String> optionalTextList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            return StringUtils.hasText(node.asText()) ? List.of(node.asText()) : List.of();
        }
        if (!node.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        var trimmed = content.trim();
        var start = trimmed.indexOf('{');
        var end = trimmed.lastIndexOf('}');
        return start >= 0 && end > start
                ? normalizeTransportWhitespace(trimmed.substring(start, end + 1))
                : null;
    }

    private String normalizeTransportWhitespace(String json) {
        var normalized = new StringBuilder(json.length());
        var insideString = false;
        var escaped = false;
        for (var index = 0; index < json.length(); index++) {
            var character = json.charAt(index);
            if (insideString) {
                if (escaped) {
                    normalized.append(character);
                    escaped = false;
                } else if (character == '\\') {
                    normalized.append(character);
                    escaped = true;
                } else if (character == '"') {
                    normalized.append(character);
                    insideString = false;
                } else if (isLineSeparator(character)) {
                    normalized.append("\\n");
                    if (character == '\r'
                            && index + 1 < json.length()
                            && json.charAt(index + 1) == '\n') {
                        index++;
                    }
                } else if (character == '\t') {
                    normalized.append("\\t");
                } else {
                    normalized.append(character);
                }
            } else if (character == '"') {
                normalized.append(character);
                insideString = true;
            } else if (isTransportWhitespace(character)) {
                normalized.append(' ');
            } else {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private boolean isLineSeparator(char character) {
        return character == '\r'
                || character == '\n'
                || character == '\u2028'
                || character == '\u2029';
    }

    private boolean isTransportWhitespace(char character) {
        return Character.isWhitespace(character)
                || Character.isSpaceChar(character)
                || character == '\uFEFF'
                || character == '\u200B';
    }

    private String text(JsonNode node, String fieldName) {
        var value = node != null ? node.get(fieldName) : null;
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
