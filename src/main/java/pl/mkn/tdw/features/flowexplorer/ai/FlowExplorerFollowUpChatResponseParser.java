package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FlowExplorerFollowUpChatResponseParser {

    private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS = Set.of(
            "message",
            "resultUpdate"
    );
    private static final Set<String> ALLOWED_RESULT_UPDATE_FIELDS = Set.of(
            "audience",
            "overview",
            "sections",
            "globalVisibilityLimits",
            "globalOpenQuestions",
            "sourceReferences",
            "confidence",
            "followUpPrompts"
    );
    private static final Set<String> ALLOWED_OVERVIEW_FIELDS = Set.of(
            "markdown",
            "confidence",
            "sourceRefs"
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

    public FlowExplorerFollowUpChatResponse parse(String assistantContent) {
        if (!StringUtils.hasText(assistantContent)) {
            throw parseException("Flow Explorer follow-up response was empty.");
        }

        var node = parseJsonObject(assistantContent);
        if (node == null) {
            throw parseException("Flow Explorer follow-up response must be a JSON object.");
        }

        var validationError = validationError(node);
        if (StringUtils.hasText(validationError)) {
            throw parseException(validationError);
        }

        return new FlowExplorerFollowUpChatResponse(
                text(node.get("message")),
                node.get("resultUpdate")
        );
    }

    private String validationError(JsonNode node) {
        var unexpectedTopLevelField = unexpectedField(node, ALLOWED_TOP_LEVEL_FIELDS);
        if (unexpectedTopLevelField != null) {
            return "Flow Explorer follow-up response contract violation: unexpected top-level field: "
                    + unexpectedTopLevelField + ".";
        }

        if (!textValueHasText(node.get("message"))) {
            return "Flow Explorer follow-up response contract violation: message is required.";
        }

        var resultUpdate = node.get("resultUpdate");
        if (resultUpdate == null || resultUpdate.isNull()) {
            return null;
        }
        if (!resultUpdate.isObject()) {
            return "Flow Explorer follow-up response contract violation: resultUpdate must be an object.";
        }
        if (resultUpdate.isEmpty()) {
            return "Flow Explorer follow-up response contract violation: resultUpdate must contain at least one field.";
        }

        var unexpectedResultUpdateField = unexpectedField(resultUpdate, ALLOWED_RESULT_UPDATE_FIELDS);
        if (unexpectedResultUpdateField != null) {
            return "Flow Explorer follow-up response contract violation: unexpected resultUpdate field: "
                    + unexpectedResultUpdateField + ".";
        }

        var confidenceError = validateConfidence(resultUpdate, "resultUpdate", "confidence");
        if (confidenceError != null) {
            return confidenceError;
        }

        for (var fieldName : Set.of(
                "globalVisibilityLimits",
                "globalOpenQuestions",
                "sourceReferences",
                "followUpPrompts"
        )) {
            var listError = validateListField(resultUpdate, "resultUpdate", fieldName);
            if (listError != null) {
                return listError;
            }
        }

        var overviewError = validateOverview(resultUpdate.get("overview"));
        if (overviewError != null) {
            return overviewError;
        }

        return validateSections(resultUpdate.get("sections"));
    }

    private String validateOverview(JsonNode overview) {
        if (overview == null || overview.isNull()) {
            return null;
        }
        if (!overview.isObject()) {
            return "Flow Explorer follow-up response contract violation: resultUpdate.overview must be an object.";
        }

        var unexpectedOverviewField = unexpectedField(overview, ALLOWED_OVERVIEW_FIELDS);
        if (unexpectedOverviewField != null) {
            return "Flow Explorer follow-up response contract violation: unexpected overview field: "
                    + unexpectedOverviewField + ".";
        }

        var confidenceError = validateConfidence(overview, "resultUpdate.overview", "confidence");
        if (confidenceError != null) {
            return confidenceError;
        }

        return validateListField(overview, "resultUpdate.overview", "sourceRefs");
    }

    private String validateSections(JsonNode sections) {
        if (sections == null || sections.isNull()) {
            return null;
        }
        if (!sections.isArray()) {
            return "Flow Explorer follow-up response contract violation: resultUpdate.sections must be an array.";
        }

        var sectionIds = new LinkedHashSet<FlowExplorerResultSectionId>();
        for (var section : sections) {
            if (section == null || !section.isObject()) {
                return "Flow Explorer follow-up response contract violation: each resultUpdate section must be an object.";
            }

            var unexpectedSectionField = unexpectedField(section, ALLOWED_SECTION_FIELDS);
            if (unexpectedSectionField != null) {
                return "Flow Explorer follow-up response contract violation: unexpected section field: "
                        + unexpectedSectionField + ".";
            }

            var sectionId = sectionId(text(section.get("id")));
            if (sectionId == null) {
                return "Flow Explorer follow-up response contract violation: section.id must be one of FUNCTIONAL_FLOW, VALIDATIONS, PERSISTENCE, INTEGRATIONS.";
            }
            if (!sectionIds.add(sectionId)) {
                return "Flow Explorer follow-up response contract violation: sections must not contain duplicate ids.";
            }

            var sectionModeError = validateSectionMode(section);
            if (sectionModeError != null) {
                return sectionModeError;
            }
            for (var fieldName : Set.of("sourceRefs", "visibilityLimits", "openQuestions")) {
                var listError = validateListField(section, "resultUpdate.sections." + sectionId.name(), fieldName);
                if (listError != null) {
                    return listError;
                }
            }
        }
        return null;
    }

    private String validateSectionMode(JsonNode section) {
        var mode = section.get("mode");
        if (mode == null || mode.isNull()) {
            return null;
        }
        var modeValue = text(mode);
        if (!StringUtils.hasText(modeValue)) {
            return "Flow Explorer follow-up response contract violation: section.mode must be compact or deep.";
        }

        try {
            var parsedMode = FlowExplorerResultSectionMode.valueOf(modeValue.toUpperCase(Locale.ROOT));
            if (parsedMode == FlowExplorerResultSectionMode.OFF) {
                return "Flow Explorer follow-up response contract violation: section.mode must be compact or deep.";
            }
            return null;
        } catch (RuntimeException exception) {
            return "Flow Explorer follow-up response contract violation: section.mode must be compact or deep.";
        }
    }

    private String validateConfidence(JsonNode objectNode, String owner, String fieldName) {
        var field = objectNode.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }

        var value = text(field);
        if (!StringUtils.hasText(value)) {
            return "Flow Explorer follow-up response contract violation: " + owner + "." + fieldName
                    + " must be high, medium or low.";
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "high", "medium", "low" -> null;
            default -> "Flow Explorer follow-up response contract violation: " + owner + "." + fieldName
                    + " must be high, medium or low.";
        };
    }

    private String validateListField(JsonNode objectNode, String owner, String fieldName) {
        var field = objectNode.get(fieldName);
        if (field == null || field.isNull() || field.isArray()) {
            return null;
        }
        return "Flow Explorer follow-up response contract violation: " + owner + "." + fieldName
                + " must be an array.";
    }

    private String unexpectedField(JsonNode objectNode, Set<String> allowedFields) {
        if (objectNode == null || !objectNode.isObject()) {
            return null;
        }
        var fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            var fieldName = fieldNames.next();
            if (!allowedFields.contains(fieldName)) {
                return fieldName;
            }
        }
        return null;
    }

    private FlowExplorerResultSectionId sectionId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return FlowExplorerResultSectionId.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean textValueHasText(JsonNode node) {
        return node != null
                && node.isValueNode()
                && StringUtils.hasText(node.asText());
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

    private JsonNode parseJsonObject(String assistantContent) {
        try (var parser = objectMapper.createParser(assistantContent.trim())) {
            var node = objectMapper.readValue(parser, JsonNode.class);
            return node != null && node.isObject() && parser.nextToken() == null ? node : null;
        } catch (IOException exception) {
            return null;
        }
    }

    private FlowExplorerFollowUpChatResponseParseException parseException(String message) {
        return new FlowExplorerFollowUpChatResponseParseException(message);
    }
}
