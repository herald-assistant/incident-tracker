package pl.mkn.tdw.features.configdriftviewer.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConclusion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConfidence;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiExecutionStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiObservation;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiObservationType;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiSecondOpinion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerFunctionalImpact;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ConfigDriftViewerAiResponseParser {

    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "conclusion",
            "confidence",
            "summary",
            "observations",
            "recommendedHumanChecks",
            "functionalImpacts",
            "visibilityLimits"
    );
    private static final Set<String> OBSERVATION_FIELDS = Set.of(
            "observationId",
            "type",
            "summary",
            "explanation",
            "differenceIds",
            "findingIds",
            "contextIds",
            "codeGroundingIds"
    );
    private static final Set<String> IMPACT_FIELDS = Set.of(
            "impactId",
            "affectedFunctionality",
            "impact",
            "confidence",
            "hypothesis",
            "systemIds",
            "differenceIds",
            "findingIds",
            "contextIds",
            "codeGroundingIds"
    );

    private final ObjectMapper objectMapper;

    public ConfigDriftViewerAiSecondOpinion parse(
            String assistantContent,
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerDeepContext deepContext
    ) {
        if (!StringUtils.hasText(assistantContent)) {
            return fallback("AI response was empty.");
        }
        var root = readObject(assistantContent.trim());
        if (root == null) {
            return fallback("AI response was not a valid JSON object.");
        }
        var unexpected = unexpectedField(root, TOP_LEVEL_FIELDS);
        if (unexpected != null) {
            return fallback("AI response attempted to write protected field `" + unexpected + "`.");
        }

        var conclusion = enumValue(ConfigDriftViewerAiConclusion.class, text(root, "conclusion"));
        var confidence = enumValue(ConfigDriftViewerAiConfidence.class, text(root, "confidence"));
        if (conclusion == null || confidence == null || !StringUtils.hasText(text(root, "summary"))) {
            return fallback("AI response did not satisfy the required conclusion contract.");
        }
        for (var listField : List.of(
                "observations",
                "recommendedHumanChecks",
                "functionalImpacts",
                "visibilityLimits"
        )) {
            if (root.get(listField) == null || !root.get(listField).isArray()) {
                return fallback("AI response field `" + listField + "` must be an array.");
            }
        }

        var ids = AllowedIds.from(deterministic, deepContext);
        var observations = parseObservations(root.get("observations"), ids);
        if (observations == null) {
            return fallback("AI response contained an invalid or ungrounded observation.");
        }
        var functionalImpacts = parseFunctionalImpacts(root.get("functionalImpacts"), ids);
        if (functionalImpacts == null) {
            return fallback("AI response contained an invalid functional impact reference.");
        }
        return new ConfigDriftViewerAiSecondOpinion(
                ConfigDriftViewerAiExecutionStatus.COMPLETED,
                conclusion,
                confidence,
                text(root, "summary"),
                observations,
                textList(root.get("recommendedHumanChecks")),
                functionalImpacts,
                textList(root.get("visibilityLimits"))
        );
    }

    public ConfigDriftViewerAiSecondOpinion fallback(String limitation) {
        return ConfigDriftViewerAiSecondOpinion.incomplete(limitation);
    }

    private List<ConfigDriftViewerAiObservation> parseObservations(JsonNode node, AllowedIds ids) {
        if (node == null || !node.isArray()) {
            return null;
        }
        var result = new ArrayList<ConfigDriftViewerAiObservation>();
        var observationIds = new LinkedHashSet<String>();
        for (var item : node) {
            if (!item.isObject() || unexpectedField(item, OBSERVATION_FIELDS) != null) {
                return null;
            }
            var observationId = text(item, "observationId");
            var type = enumValue(ConfigDriftViewerAiObservationType.class, text(item, "type"));
            var summary = text(item, "summary");
            if (!StringUtils.hasText(observationId) || !observationIds.add(observationId)
                    || type == null || !StringUtils.hasText(summary)) {
                return null;
            }
            var differenceIds = textList(item.get("differenceIds"));
            var findingIds = textList(item.get("findingIds"));
            var contextIds = textList(item.get("contextIds"));
            var codeIds = textList(item.get("codeGroundingIds"));
            if (!ids.accepts(differenceIds, findingIds, contextIds, codeIds)) {
                return null;
            }
            if (type == ConfigDriftViewerAiObservationType.GROUNDED_OBSERVATION
                    && differenceIds.isEmpty() && findingIds.isEmpty()) {
                return null;
            }
            result.add(new ConfigDriftViewerAiObservation(
                    observationId,
                    type,
                    summary,
                    text(item, "explanation"),
                    differenceIds,
                    findingIds,
                    contextIds,
                    codeIds
            ));
        }
        return List.copyOf(result);
    }

    private List<ConfigDriftViewerFunctionalImpact> parseFunctionalImpacts(JsonNode node, AllowedIds ids) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return null;
        }
        var result = new ArrayList<ConfigDriftViewerFunctionalImpact>();
        var impactIds = new LinkedHashSet<String>();
        for (var item : node) {
            if (!item.isObject() || unexpectedField(item, IMPACT_FIELDS) != null) {
                return null;
            }
            var impactId = text(item, "impactId");
            var confidence = enumValue(ConfigDriftViewerAiConfidence.class, text(item, "confidence"));
            var differenceIds = textList(item.get("differenceIds"));
            var findingIds = textList(item.get("findingIds"));
            var contextIds = textList(item.get("contextIds"));
            var codeIds = textList(item.get("codeGroundingIds"));
            var systemIds = textList(item.get("systemIds"));
            if (!StringUtils.hasText(impactId) || !impactIds.add(impactId)
                    || !StringUtils.hasText(text(item, "affectedFunctionality"))
                    || !StringUtils.hasText(text(item, "impact"))
                    || confidence == null
                    || !ids.accepts(differenceIds, findingIds, contextIds, codeIds)
                    || !ids.systemIds.containsAll(systemIds)) {
                return null;
            }
            var hypothesis = booleanValue(item, "hypothesis");
            if (!hypothesis && differenceIds.isEmpty() && findingIds.isEmpty()) {
                return null;
            }
            result.add(new ConfigDriftViewerFunctionalImpact(
                    impactId,
                    text(item, "affectedFunctionality"),
                    text(item, "impact"),
                    confidence,
                    hypothesis,
                    systemIds,
                    differenceIds,
                    findingIds,
                    contextIds,
                    codeIds
            ));
        }
        return List.copyOf(result);
    }

    private JsonNode readObject(String content) {
        try {
            var node = objectMapper.readTree(content);
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static String unexpectedField(JsonNode node, Set<String> allowed) {
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!allowed.contains(field)) {
                return field;
            }
        }
        return null;
    }

    private static List<String> textList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return List.of("__INVALID_LIST__");
        }
        var values = new LinkedHashSet<String>();
        node.forEach(item -> {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        });
        return List.copyOf(values);
    }

    private static String text(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }

    private static boolean booleanValue(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.isBoolean() && value.asBoolean();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record AllowedIds(
            Set<String> differenceIds,
            Set<String> findingIds,
            Set<String> contextIds,
            Set<String> codeIds,
            Set<String> systemIds
    ) {

        static AllowedIds from(
                ConfigDriftViewerDeterministicContext deterministic,
                ConfigDriftViewerDeepContext deep
        ) {
            var differences = new LinkedHashSet<String>();
            var findings = new LinkedHashSet<String>();
            if (deterministic != null) {
                deterministic.differences().forEach(value -> differences.add(value.differenceId()));
                deterministic.findings().forEach(value -> findings.add(value.findingId()));
            }
            var contexts = new LinkedHashSet<String>();
            var code = new LinkedHashSet<String>();
            var systems = new LinkedHashSet<String>();
            if (deep != null) {
                if (deep.primarySystem() != null) {
                    systems.add(deep.primarySystem().systemId());
                }
                deep.affectedSystems().forEach(value -> {
                    contexts.add(value.contextId());
                    systems.add(value.entityId());
                });
                deep.integrations().forEach(value -> contexts.add(value.contextId()));
                deep.processes().forEach(value -> contexts.add(value.contextId()));
                deep.boundedContexts().forEach(value -> contexts.add(value.contextId()));
                deep.codeGrounding().forEach(value -> code.add(value.groundingId()));
            }
            return new AllowedIds(
                    Set.copyOf(differences),
                    Set.copyOf(findings),
                    Set.copyOf(contexts),
                    Set.copyOf(code),
                    Set.copyOf(systems)
            );
        }

        boolean accepts(
                List<String> differences,
                List<String> findings,
                List<String> contexts,
                List<String> code
        ) {
            return differenceIds.containsAll(differences)
                    && findingIds.containsAll(findings)
                    && contextIds.containsAll(contexts)
                    && codeIds.containsAll(code);
        }
    }
}
