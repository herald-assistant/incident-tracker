package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class FlowExplorerResultUpdateApplicator {

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

    public FlowExplorerAiResponse apply(FlowExplorerAiResponse current, JsonNode resultUpdate) {
        if (current == null) {
            throw applyException("Current Flow Explorer result is required.");
        }
        if (resultUpdate == null || resultUpdate.isNull()) {
            return current;
        }
        if (!resultUpdate.isObject()) {
            throw applyException("resultUpdate must be an object.");
        }

        validateNoExplicitNull(resultUpdate, "resultUpdate");
        validateAllowedFields(resultUpdate, ALLOWED_RESULT_UPDATE_FIELDS, "resultUpdate");

        return new FlowExplorerAiResponse(
                current.goal(),
                stringField(resultUpdate, "audience", current.audience()),
                overview(current.overview(), resultUpdate.get("overview")),
                sections(current.sections(), resultUpdate.get("sections")),
                listField(resultUpdate, "globalVisibilityLimits", current.globalVisibilityLimits()),
                listField(resultUpdate, "globalOpenQuestions", current.globalOpenQuestions()),
                listField(resultUpdate, "sourceReferences", current.sourceReferences()),
                confidenceField(resultUpdate, "confidence", current.confidence()),
                listField(resultUpdate, "followUpPrompts", current.followUpPrompts())
        );
    }

    private FlowExplorerResultOverview overview(FlowExplorerResultOverview current, JsonNode update) {
        if (update == null) {
            return current;
        }
        if (!update.isObject()) {
            throw applyException("resultUpdate.overview must be an object.");
        }

        validateAllowedFields(update, ALLOWED_OVERVIEW_FIELDS, "resultUpdate.overview");
        return new FlowExplorerResultOverview(
                stringField(update, "markdown", current.markdown()),
                confidenceField(update, "confidence", current.confidence()),
                listField(update, "sourceRefs", current.sourceRefs())
        );
    }

    private List<FlowExplorerResultSection> sections(List<FlowExplorerResultSection> current, JsonNode update) {
        if (update == null) {
            return current;
        }
        if (!update.isArray()) {
            throw applyException("resultUpdate.sections must be an array.");
        }

        var mergedById = new LinkedHashMap<FlowExplorerResultSectionId, FlowExplorerResultSection>();
        for (var section : current) {
            if (section != null && section.id() != null) {
                mergedById.put(section.id(), section);
            }
        }

        for (var sectionUpdate : update) {
            if (sectionUpdate == null || !sectionUpdate.isObject()) {
                throw applyException("Each resultUpdate section must be an object.");
            }
            validateAllowedFields(sectionUpdate, ALLOWED_SECTION_FIELDS, "resultUpdate.sections");
            var sectionId = sectionId(sectionUpdate.get("id"));
            if (sectionId == null) {
                throw applyException("section.id must be one of FUNCTIONAL_FLOW, VALIDATIONS, PERSISTENCE, INTEGRATIONS.");
            }
            var currentSection = mergedById.get(sectionId);
            if (currentSection == null) {
                throw applyException("section " + sectionId.name() + " is not present in the current result.");
            }
            mergedById.put(sectionId, section(currentSection, sectionUpdate));
        }

        return List.copyOf(mergedById.values());
    }

    private FlowExplorerResultSection section(FlowExplorerResultSection current, JsonNode update) {
        return new FlowExplorerResultSection(
                current.id(),
                stringField(update, "title", current.title()),
                sectionModeField(update, current.mode()),
                stringField(update, "markdown", current.markdown()),
                listField(update, "sourceRefs", current.sourceRefs()),
                listField(update, "visibilityLimits", current.visibilityLimits()),
                listField(update, "openQuestions", current.openQuestions())
        );
    }

    private String stringField(JsonNode objectNode, String fieldName, String currentValue) {
        var field = objectNode.get(fieldName);
        return field != null ? text(field) : currentValue;
    }

    private String confidenceField(JsonNode objectNode, String fieldName, String currentValue) {
        var field = objectNode.get(fieldName);
        if (field == null) {
            return currentValue;
        }

        var value = text(field).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "high", "medium", "low" -> value;
            default -> throw applyException(fieldName + " must be high, medium or low.");
        };
    }

    private FlowExplorerResultSectionMode sectionModeField(JsonNode objectNode, FlowExplorerResultSectionMode currentValue) {
        var field = objectNode.get("mode");
        if (field == null) {
            return currentValue;
        }

        try {
            var parsed = FlowExplorerResultSectionMode.valueOf(text(field).toUpperCase(Locale.ROOT));
            if (parsed == FlowExplorerResultSectionMode.OFF) {
                throw applyException("section.mode must be compact or deep.");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw applyException("section.mode must be compact or deep.");
        }
    }

    private List<String> listField(JsonNode objectNode, String fieldName, List<String> currentValue) {
        var field = objectNode.get(fieldName);
        if (field == null) {
            return currentValue;
        }
        if (!field.isArray()) {
            throw applyException(fieldName + " must be an array.");
        }

        var values = new ArrayList<String>();
        for (var item : field) {
            values.add(text(item));
        }
        return List.copyOf(values);
    }

    private FlowExplorerResultSectionId sectionId(JsonNode node) {
        try {
            return FlowExplorerResultSectionId.valueOf(text(node).toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void validateNoExplicitNull(JsonNode node, String path) {
        if (node == null) {
            return;
        }
        if (node.isNull()) {
            throw applyException(path + " must not be null.");
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                validateNoExplicitNull(field.getValue(), path + "." + field.getKey());
            }
        } else if (node.isArray()) {
            for (var index = 0; index < node.size(); index++) {
                validateNoExplicitNull(node.get(index), path + "[" + index + "]");
            }
        }
    }

    private void validateAllowedFields(JsonNode objectNode, Set<String> allowedFields, String owner) {
        var fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            var fieldName = fieldNames.next();
            if (!allowedFields.contains(fieldName)) {
                throw applyException("Unexpected " + owner + " field: " + fieldName + ".");
            }
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            throw applyException("Expected non-null JSON value.");
        }
        if (node.isValueNode()) {
            return node.asText().trim();
        }
        return node.toString();
    }

    private IllegalArgumentException applyException(String message) {
        return new IllegalArgumentException("Flow Explorer result update cannot be applied: " + message);
    }
}
