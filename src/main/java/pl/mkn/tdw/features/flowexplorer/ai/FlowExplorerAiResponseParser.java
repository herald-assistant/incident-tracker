package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FlowExplorerAiResponseParser {

    private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS = Set.of(
            "goal",
            "audience",
            "overview",
            "sections",
            "globalVisibilityLimits",
            "globalOpenQuestions",
            "sourceReferences",
            "confidence",
            "followUpPrompts"
    );
    private static final List<String> FUNCTIONAL_FLOW_MARKDOWN_LABELS = List.of(
            "**Cel funkcjonalny:**",
            "**Flow krok po kroku:**",
            "**Koordynacja i routing:**",
            "**Kalkulacje i reguly funkcjonalne:**",
            "**Rozgalezienia zalezne od kontekstu:**",
            "**Handoffy i efekty uboczne:**",
            "**Akcent goal:**"
    );
    private static final String FUNCTIONAL_FLOW_MARKDOWN_STRUCTURE_WARNING =
            "AI response quality warning: FUNCTIONAL_FLOW markdown does not use the recommended bullet structure.";

    private final ObjectMapper objectMapper;

    public FlowExplorerAiResponse parse(String assistantContent) {
        return parseValidated(assistantContent, null, null);
    }

    public FlowExplorerAiResponse parseForFocusAreas(
            String assistantContent,
            FlowExplorerAnalysisGoal requestedGoal,
            List<FlowExplorerFocusArea> focusAreas
    ) {
        return parse(assistantContent, requestedGoal, FlowExplorerResultSectionModeResolver.resolve(focusAreas));
    }

    public FlowExplorerAiResponse parse(
            String assistantContent,
            FlowExplorerAnalysisGoal requestedGoal,
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        return parseValidated(assistantContent, requestedGoal, sectionModes)
                .withRequestContext(requestedGoal, sectionModes);
    }

    private FlowExplorerAiResponse parseValidated(
            String assistantContent,
            FlowExplorerAnalysisGoal requestedGoal,
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        if (!StringUtils.hasText(assistantContent)) {
            return FlowExplorerAiResponse.parseFallback("AI response was empty.");
        }

        var node = parseJsonNode(assistantContent);
        if (node == null || !node.isObject()) {
            return FlowExplorerAiResponse.parseFallback("AI response was not valid JSON.");
        }
        var validationError = validationError(node, requestedGoal, sectionModes);
        if (StringUtils.hasText(validationError)) {
            return FlowExplorerAiResponse.parseFallback(validationError);
        }

        var qualityWarnings = qualityWarnings(node);
        return new FlowExplorerAiResponse(
                goal(text(node, "goal")),
                text(node, "audience"),
                overview(node.get("overview"), qualityWarnings),
                sections(node.get("sections")),
                listWithQualityWarnings(textList(node.get("globalVisibilityLimits")), qualityWarnings),
                textList(node.get("globalOpenQuestions")),
                textList(node.get("sourceReferences")),
                confidenceWithQualityWarnings(text(node, "confidence"), qualityWarnings),
                textList(node.get("followUpPrompts"))
        );
    }

    private String validationError(
            JsonNode node,
            FlowExplorerAnalysisGoal requestedGoal,
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        var unexpectedTopLevelField = unexpectedField(node, ALLOWED_TOP_LEVEL_FIELDS);
        if (unexpectedTopLevelField != null) {
            return "AI response contract violation: unexpected top-level field: " + unexpectedTopLevelField + ".";
        }

        var parsedGoal = goalStrict(text(node, "goal"));
        if (parsedGoal == null) {
            return "AI response contract violation: goal must be one of DEEP_DISCOVERY, TEST_SCENARIOS, RISK_DETECTION.";
        }
        if (requestedGoal != null && parsedGoal != requestedGoal) {
            return "AI response goal does not match requested goal.";
        }

        var overview = node.get("overview");
        if (overview == null || !overview.isObject()) {
            return "AI response contract violation: overview must be an object.";
        }
        if (!StringUtils.hasText(text(overview, "markdown"))) {
            return "AI response contract violation: overview.markdown is required.";
        }
        var overviewListError = validateListField(overview, "overview", "sourceRefs");
        if (overviewListError != null) {
            return overviewListError;
        }

        for (var fieldName : List.of(
                "globalVisibilityLimits",
                "globalOpenQuestions",
                "sourceReferences",
                "followUpPrompts"
        )) {
            var listError = validateListField(node, "response", fieldName);
            if (listError != null) {
                return listError;
            }
        }

        var sections = node.get("sections");
        if (sections == null || !sections.isArray()) {
            return "AI response contract violation: sections must be an array.";
        }

        var expectedAssignments = sectionModes != null
                ? sectionModes
                : FlowExplorerResultSectionModeResolver.resolve(null);
        var validateModes = sectionModes != null;
        var expectedActiveAssignments = FlowExplorerResultSectionModeResolver.activeOnly(expectedAssignments);
        if (sections.size() != expectedActiveAssignments.size()) {
            return "AI response contract violation: sections must contain exactly the active sectionModes items.";
        }

        var expectedModes = expectedAssignments.stream()
                .collect(java.util.stream.Collectors.toMap(
                        assignment -> assignment.id(),
                        assignment -> assignment.mode()
                ));
        var expectedActiveIds = expectedActiveAssignments.stream()
                .map(FlowExplorerResultSectionModeAssignment::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var sectionIds = new LinkedHashSet<FlowExplorerResultSectionId>();

        for (var section : sections) {
            if (section == null || !section.isObject()) {
                return "AI response contract violation: each section must be an object.";
            }
            var sectionId = sectionIdStrict(text(section, "id"));
            if (sectionId == null) {
                return "AI response contract violation: section.id must be one of FUNCTIONAL_FLOW, VALIDATIONS, PERSISTENCE, INTEGRATIONS.";
            }
            if (sectionId == FlowExplorerResultSectionId.FUNCTIONAL_FLOW
                    && !sectionId.title().equals(text(section, "title"))) {
                return "AI response contract violation: FUNCTIONAL_FLOW title must be Functional flow.";
            }
            if (!sectionIds.add(sectionId)) {
                return "AI response contract violation: sections must not contain duplicate ids.";
            }
            if (!expectedActiveIds.contains(sectionId)) {
                return "AI response contract violation: section " + sectionId.name()
                        + " is OFF or not requested in active sectionModes.";
            }
            var sectionMode = sectionModeStrict(text(section, "mode"));
            if (sectionMode == null || sectionMode == FlowExplorerResultSectionMode.OFF) {
                return "AI response contract violation: section.mode must be compact or deep.";
            }
            if (validateModes && expectedModes.get(sectionId) != sectionMode) {
                return "AI response contract violation: section.mode does not match request sectionModes.";
            }
            var markdown = text(section, "markdown");
            if (!StringUtils.hasText(markdown)) {
                return "AI response contract violation: section.markdown is required for " + sectionId.name() + ".";
            }
            var markdownError = validateSectionMarkdownContract(sectionId, markdown);
            if (markdownError != null) {
                return markdownError;
            }
            for (var fieldName : List.of("sourceRefs", "visibilityLimits", "openQuestions")) {
                var listError = validateListField(section, sectionId.name(), fieldName);
                if (listError != null) {
                    return listError;
                }
            }
        }

        if (!sectionIds.equals(expectedActiveIds)) {
            return "AI response contract violation: sections must include all active sectionModes ids.";
        }
        return null;
    }

    private String validateSectionMarkdownContract(FlowExplorerResultSectionId sectionId, String markdown) {
        if (sectionId != FlowExplorerResultSectionId.FUNCTIONAL_FLOW) {
            return null;
        }
        if (markdown.contains("**Evidence") || markdown.contains("**Ograniczenia")) {
            return "AI response contract violation: FUNCTIONAL_FLOW markdown must keep evidence and limits in sourceRefs, visibilityLimits or openQuestions.";
        }
        return null;
    }

    private List<String> qualityWarnings(JsonNode node) {
        var sections = node.get("sections");
        if (sections == null || !sections.isArray()) {
            return List.of();
        }
        for (var section : sections) {
            if (!section.isObject()) {
                continue;
            }
            var sectionId = sectionIdStrict(text(section, "id"));
            if (sectionId == FlowExplorerResultSectionId.FUNCTIONAL_FLOW
                    && !usesRecommendedFunctionalFlowMarkdownStructure(text(section, "markdown"))) {
                return List.of(FUNCTIONAL_FLOW_MARKDOWN_STRUCTURE_WARNING);
            }
        }
        return List.of();
    }

    private boolean usesRecommendedFunctionalFlowMarkdownStructure(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return true;
        }
        var previousIndex = -1;
        for (var label : FUNCTIONAL_FLOW_MARKDOWN_LABELS) {
            var index = markdown.indexOf(label);
            if (index <= previousIndex) {
                return false;
            }
            previousIndex = index;
        }
        return true;
    }

    private List<String> listWithQualityWarnings(List<String> values, List<String> qualityWarnings) {
        if (qualityWarnings.isEmpty()) {
            return values;
        }
        var merged = new ArrayList<String>(values);
        merged.addAll(qualityWarnings);
        return List.copyOf(merged);
    }

    private String confidenceWithQualityWarnings(String value, List<String> qualityWarnings) {
        var normalized = confidence(value);
        if (qualityWarnings.isEmpty() || !"high".equals(normalized)) {
            return normalized;
        }
        return "medium";
    }

    private String validateListField(JsonNode objectNode, String owner, String fieldName) {
        var field = objectNode.get(fieldName);
        if (field == null || field.isNull() || field.isArray()) {
            return null;
        }
        return "AI response contract violation: " + owner + "." + fieldName + " must be an array.";
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

    private FlowExplorerResultOverview overview(JsonNode node, List<String> qualityWarnings) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return new FlowExplorerResultOverview(
                text(node, "markdown"),
                confidenceWithQualityWarnings(text(node, "confidence"), qualityWarnings),
                textList(node.get("sourceRefs"))
        );
    }

    private List<FlowExplorerResultSection> sections(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        var sections = new ArrayList<FlowExplorerResultSection>();
        for (var section : node) {
            if (!section.isObject()) {
                continue;
            }
            var sectionId = sectionId(text(section, "id"));
            if (sectionId == null) {
                continue;
            }
            sections.add(new FlowExplorerResultSection(
                    sectionId,
                    text(section, "title"),
                    sectionMode(text(section, "mode")),
                    text(section, "markdown"),
                    textList(section.get("sourceRefs")),
                    textList(section.get("visibilityLimits")),
                    textList(section.get("openQuestions"))
            ));
        }
        return List.copyOf(sections);
    }

    private FlowExplorerAnalysisGoal goal(String value) {
        var strict = goalStrict(value);
        return strict != null ? strict : FlowExplorerAnalysisGoal.DEEP_DISCOVERY;
    }

    private FlowExplorerAnalysisGoal goalStrict(String value) {
        try {
            return FlowExplorerAnalysisGoal.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private FlowExplorerResultSectionId sectionId(String value) {
        return sectionIdStrict(value);
    }

    private FlowExplorerResultSectionId sectionIdStrict(String value) {
        try {
            return FlowExplorerResultSectionId.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private FlowExplorerResultSectionMode sectionMode(String value) {
        var strict = sectionModeStrict(value);
        return strict != null ? strict : FlowExplorerResultSectionMode.COMPACT;
    }

    private FlowExplorerResultSectionMode sectionModeStrict(String value) {
        try {
            return FlowExplorerResultSectionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
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
        return readTree(assistantContent.trim());
    }

    private JsonNode readTree(String candidate) {
        try {
            return objectMapper.readTree(candidate);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

}
