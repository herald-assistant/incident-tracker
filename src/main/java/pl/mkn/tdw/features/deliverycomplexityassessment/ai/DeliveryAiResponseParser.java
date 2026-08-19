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
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DeliveryAiResponseParser {

    private static final Set<String> CLASSIFICATIONS =
            Set.of("DELIVERY", "EXCLUDED", "INSUFFICIENT_EVIDENCE");

    private final ObjectMapper objectMapper;

    public DeliveryAiResponse parse(String content, Map<String, String> artifacts) {
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
            var evidenceSummary = textList(root.path("evidenceSummary"));
            if (dimensions != null) {
                validateEvidenceCoverage(dimensions, evidenceSummary, artifacts);
            }
            return new DeliveryAiResponse(
                    classification,
                    dimensions,
                    confidence,
                    evidenceSummary,
                    textList(root.path("qualityFlags")),
                    textList(root.path("visibilityLimits"))
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

    private void validateEvidenceCoverage(
            DeliveryAssessmentDimensions dimensions,
            List<String> evidenceSummary,
            Map<String, String> artifacts
    ) {
        requireDimensionEvidence("outcomeBreadth", dimensions.outcomeBreadth(), evidenceSummary, artifacts);
        requireDimensionEvidence(
                "domainDecisionComplexity", dimensions.domainDecisionComplexity(), evidenceSummary, artifacts
        );
        requireDimensionEvidence(
                "applicationFlowComplexity", dimensions.applicationFlowComplexity(), evidenceSummary, artifacts
        );
        requireDimensionEvidence(
                "boundaryAndDataComplexity", dimensions.boundaryAndDataComplexity(), evidenceSummary, artifacts
        );
        requireDimensionEvidence(
                "verificationStateSpace", dimensions.verificationStateSpace(), evidenceSummary, artifacts
        );
        requireDimensionEvidence(
                "implementedCompatibilityScope",
                dimensions.implementedCompatibilityScope(),
                evidenceSummary,
                artifacts
        );
    }

    private void requireDimensionEvidence(
            String dimension,
            int score,
            List<String> evidenceSummary,
            Map<String, String> artifacts
    ) {
        if (score == 0) {
            return;
        }
        var covered = evidenceSummary.stream().anyMatch(item -> isDimensionEvidence(item, dimension, artifacts));
        if (!covered) {
            throw new IllegalArgumentException(
                    "AI response does not contain evidence for non-zero dimension " + dimension + "."
            );
        }
    }

    private boolean isDimensionEvidence(
            String item,
            String dimension,
            Map<String, String> artifacts
    ) {
        if (!StringUtils.hasText(item)) {
            return false;
        }
        var parts = item.split("\\|", 3);
        return parts.length == 3
                && dimension.equals(parts[0].trim())
                && groundedReference(parts[1], artifacts)
                && StringUtils.hasText(parts[2]);
    }

    private boolean groundedReference(String value, Map<String, String> artifacts) {
        if (!StringUtils.hasText(value) || artifacts == null || artifacts.isEmpty()) {
            return false;
        }
        var reference = value.trim();
        var sectionSeparator = reference.indexOf('#');
        var artifactName = sectionSeparator >= 0
                ? reference.substring(0, sectionSeparator).trim()
                : reference;
        if (artifacts.containsKey(artifactName)) {
            return groundedArtifactSection(artifacts.get(artifactName), reference, sectionSeparator);
        }
        var artifactAliases = artifacts.entrySet().stream()
                .filter(entry -> artifactName.equals(shortArtifactName(entry.getKey())))
                .toList();
        if (artifactAliases.size() == 1) {
            return groundedArtifactSection(artifactAliases.get(0).getValue(), reference, sectionSeparator);
        }
        return artifacts.values().stream()
                .filter(StringUtils::hasText)
                .anyMatch(content -> containsReference(content, reference, sectionSeparator));
    }

    private String shortArtifactName(String artifactName) {
        var separator = artifactName.lastIndexOf('/');
        return separator >= 0 ? artifactName.substring(separator + 1) : artifactName;
    }

    private boolean groundedArtifactSection(String content, String reference, int sectionSeparator) {
        if (sectionSeparator < 0) {
            return true;
        }
        if (!StringUtils.hasText(content) || sectionSeparator >= reference.length() - 1) {
            return false;
        }
        var section = reference.substring(sectionSeparator + 1).trim();
        return StringUtils.hasText(section) && content.contains(section);
    }

    private boolean containsReference(String content, String reference, int sectionSeparator) {
        if (content.contains(reference)) {
            return true;
        }
        if (sectionSeparator <= 0 || sectionSeparator >= reference.length() - 1) {
            return false;
        }
        var artifactPart = reference.substring(0, sectionSeparator).trim();
        var sectionPart = reference.substring(sectionSeparator + 1).trim();
        return StringUtils.hasText(artifactPart)
                && StringUtils.hasText(sectionPart)
                && content.contains(artifactPart)
                && content.contains(sectionPart);
    }

    private int requiredDimension(JsonNode node, String name) {
        var value = node.get(name);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException("AI response dimension " + name + " is missing.");
        }
        return value.asInt();
    }

    private List<String> textList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("AI response collection field has invalid type.");
        }
        var values = new ArrayList<String>();
        for (var item : node) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("AI response collection contains a non-text value.");
            }
            values.add(item.asText());
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
