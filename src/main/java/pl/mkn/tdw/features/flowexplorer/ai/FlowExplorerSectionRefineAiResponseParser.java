package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FlowExplorerSectionRefineAiResponseParser {

    private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS = Set.of(
            "section",
            "globalVisibilityLimits",
            "globalOpenQuestions",
            "sourceReferences",
            "followUpPrompts",
            "confidence",
            "changeSummary"
    );
    private static final Set<String> ALLOWED_SECTION_FIELDS = Set.of(
            "id",
            "title",
            "mode",
            "markdown",
            "sourceRefs",
            "visibilityLimits",
            "openQuestions"
    );

    private final ObjectMapper objectMapper;

    public FlowExplorerSectionRefineAiResponse parse(
            String assistantContent,
            FlowExplorerResultSectionId expectedSectionId,
            FlowExplorerResultSectionMode expectedMode
    ) {
        if (expectedSectionId == null) {
            throw invalid("Expected section id is required.");
        }
        if (expectedMode == null || expectedMode == FlowExplorerResultSectionMode.OFF) {
            throw invalid("Expected section mode must be compact or deep.");
        }
        if (!StringUtils.hasText(assistantContent)) {
            throw invalid("AI refine response was empty.");
        }

        var node = parseJsonNode(assistantContent);
        if (node == null || !node.isObject()) {
            throw invalid("AI refine response was not valid JSON.");
        }
        validateAllowedFields(node, "response", ALLOWED_TOP_LEVEL_FIELDS);

        var sectionNode = node.get("section");
        if (sectionNode == null || !sectionNode.isObject()) {
            throw invalid("AI refine response contract violation: section must be an object.");
        }
        validateAllowedFields(sectionNode, "section", ALLOWED_SECTION_FIELDS);

        for (var fieldName : List.of(
                "globalVisibilityLimits",
                "globalOpenQuestions",
                "sourceReferences",
                "followUpPrompts",
                "changeSummary"
        )) {
            validateListField(node, "response", fieldName);
        }
        for (var fieldName : List.of("sourceRefs", "visibilityLimits", "openQuestions")) {
            validateListField(sectionNode, "section", fieldName);
        }

        var sectionId = sectionIdStrict(text(sectionNode, "id"));
        if (sectionId == null) {
            throw invalid("AI refine response contract violation: section.id must be one of FUNCTIONAL_FLOW, VALIDATIONS, PERSISTENCE, INTEGRATIONS.");
        }
        if (sectionId != expectedSectionId) {
            throw invalid("AI refine response section.id does not match target section.");
        }

        var mode = sectionModeStrict(text(sectionNode, "mode"));
        if (mode == null || mode == FlowExplorerResultSectionMode.OFF) {
            throw invalid("AI refine response contract violation: section.mode must be compact or deep.");
        }
        if (mode != expectedMode) {
            throw invalid("AI refine response section.mode does not match target section mode.");
        }

        var markdown = text(sectionNode, "markdown");
        if (!StringUtils.hasText(markdown)) {
            throw invalid("AI refine response contract violation: section.markdown is required.");
        }
        validateSectionMarkdownContract(sectionId, markdown);

        return new FlowExplorerSectionRefineAiResponse(
                new FlowExplorerResultSection(
                        sectionId,
                        text(sectionNode, "title"),
                        mode,
                        markdown,
                        textList(sectionNode.get("sourceRefs")),
                        textList(sectionNode.get("visibilityLimits")),
                        textList(sectionNode.get("openQuestions"))
                ),
                textList(node.get("globalVisibilityLimits")),
                textList(node.get("globalOpenQuestions")),
                textList(node.get("sourceReferences")),
                textList(node.get("followUpPrompts")),
                confidence(node.get("confidence")),
                textList(node.get("changeSummary"))
        );
    }

    private void validateSectionMarkdownContract(FlowExplorerResultSectionId sectionId, String markdown) {
        if (sectionId != FlowExplorerResultSectionId.FUNCTIONAL_FLOW) {
            return;
        }
        if (markdown.contains("**Evidence") || markdown.contains("**Ograniczenia")) {
            throw invalid("AI refine response contract violation: FUNCTIONAL_FLOW markdown must keep evidence and limits in sourceRefs, visibilityLimits or openQuestions.");
        }
    }

    private void validateAllowedFields(JsonNode objectNode, String owner, Set<String> allowedFields) {
        var fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            var fieldName = fieldNames.next();
            if (!allowedFields.contains(fieldName)) {
                throw invalid("AI refine response contract violation: unexpected " + owner + " field: " + fieldName + ".");
            }
        }
    }

    private void validateListField(JsonNode objectNode, String owner, String fieldName) {
        var field = objectNode.get(fieldName);
        if (field == null || field.isNull() || field.isArray()) {
            return;
        }
        throw invalid("AI refine response contract violation: " + owner + "." + fieldName + " must be an array.");
    }

    private FlowExplorerResultSectionId sectionIdStrict(String value) {
        try {
            return FlowExplorerResultSectionId.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private FlowExplorerResultSectionMode sectionModeStrict(String value) {
        try {
            return FlowExplorerResultSectionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String confidence(JsonNode node) {
        var value = text(node);
        if (!StringUtils.hasText(value)) {
            return "low";
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "high", "medium", "low" -> normalized;
            default -> throw invalid("AI refine response contract violation: confidence must be high, medium or low.");
        };
    }

    private List<String> textList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var item : node) {
            var value = text(item);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
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

    private JsonNode parseJsonNode(String assistantContent) {
        try {
            return objectMapper.readTree(assistantContent.trim());
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
