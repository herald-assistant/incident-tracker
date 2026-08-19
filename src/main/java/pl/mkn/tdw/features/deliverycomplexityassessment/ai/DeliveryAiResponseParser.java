package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
            var classification = normalized(text(root, "classification"));
            if (!CLASSIFICATIONS.contains(classification)) {
                throw new IllegalArgumentException("AI response classification is unsupported.");
            }
            var confidence = root.path("confidence").asDouble(-1);
            if (confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("AI response confidence must be between 0 and 1.");
            }
            var dimensions = "DELIVERY".equals(classification) ? dimensions(root.path("dimensions")) : null;
            return new DeliveryAiResponse(
                    classification,
                    dimensions,
                    confidence,
                    optionalTextList(root.path("evidenceSummary")),
                    optionalTextList(root.path("qualityFlags")),
                    optionalTextList(root.path("visibilityLimits"))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI response JSON could not be parsed.", exception);
        }
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
                requiredDimension(node, "implementedCompatibilityScope")
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
        return start >= 0 && end > start ? trimmed.substring(start, end + 1) : null;
    }

    private String text(JsonNode node, String fieldName) {
        var value = node != null ? node.get(fieldName) : null;
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
