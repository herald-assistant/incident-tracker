package pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .SanitizedConfigurationNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationAiArtifactService {

    static final int MAX_STRUCTURE_CHARACTERS = 120_000;
    static final int MAX_CHANGES_CHARACTERS = 80_000;
    static final int MAX_DEEP_CHARACTERS = 60_000;
    private static final String YAML_TRUNCATION_MARKER =
            "\ntruncated: true\ntruncationReason: \"sanitized AI artifact character limit reached\"\n";

    private final ObjectMapper objectMapper;

    public RuntimeConfigurationAiArtifacts render(
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deepContext
    ) {
        if (deterministic == null) {
            throw new IllegalArgumentException("deterministic context is required");
        }

        var artifacts = new LinkedHashMap<String, String>();
        var visibilityLimits = new ArrayList<String>();
        artifacts.put("runtime-configuration/scope.json", json(scope(deterministic)));
        artifacts.put("runtime-configuration/coverage.json", json(Map.of(
                "source", deterministic.sourceCoverage(),
                "target", deterministic.targetCoverage()
        )));
        artifacts.put(
                "runtime-configuration/configuration-tree.yaml",
                boundedYaml(configurationTree(deterministic.documents()), visibilityLimits)
        );
        artifacts.put(
                "runtime-configuration/changes.json",
                boundedJson(changes(deterministic), MAX_CHANGES_CHARACTERS, "changes", visibilityLimits)
        );

        if (deepContext != null) {
            artifacts.put(
                    "runtime-configuration/deep-context.json",
                    boundedJson(
                            deepContext,
                            MAX_DEEP_CHARACTERS,
                            "deep-context",
                            visibilityLimits
                    )
            );
            visibilityLimits.addAll(deepContext.visibilityLimits());
        }
        artifacts.put("runtime-configuration/response-contract.json", responseContract());
        return new RuntimeConfigurationAiArtifacts(artifacts, visibilityLimits);
    }

    private Map<String, Object> scope(RuntimeConfigurationDeterministicContext context) {
        var result = new LinkedHashMap<String, Object>();
        result.put("formatVersion", 1);
        result.put("mode", "DEEP");
        result.put("repositoryId", context.repositoryId());
        result.put("systemId", context.systemId());
        result.put("systemLabel", context.systemLabel());
        result.put("configurationDirectory", context.configurationDirectory());
        result.put("sourceBranch", context.sourceBranch());
        result.put("targetBranch", context.targetBranch());
        result.put("includesChangedAndUnchangedParameters", true);
        result.put("valueTokensAreRunLocalPseudonyms", true);
        return result;
    }

    private String configurationTree(List<SanitizedConfigurationDocument> documents) {
        var result = new StringBuilder();
        result.append("formatVersion: 1\n");
        result.append("documentColumns: [role, documentIndex, sourcePath, targetPath, ");
        result.append("sourcePresent, targetPresent, sourceProfileToken, targetProfileToken]\n");
        result.append("columns: [name, relation, sourceType, targetType, sensitivity, ");
        result.append("sourceRepresentation, targetRepresentation, sourceCardinality, targetCardinality]\n");
        result.append("relationCodes: {U: UNCHANGED, A: ADDED, R: REMOVED, C: CHANGED, ");
        result.append("T: TYPE_CHANGED, E: EFFECTIVE_CHANGED}\n");
        result.append("typeCodes: {M: MAP, L: LIST, S: STRING, N: NUMBER, B: BOOLEAN, ");
        result.append("Z: NULL, X: UNKNOWN}\n");
        result.append("sensitivityCodes: {N: NON_SENSITIVE, S: SENSITIVE}\n");
        result.append("representationLegend:\n");
        result.append("  A: value does not exist on this side\n");
        result.append("  O: map/list node without scalar value\n");
        result.append("  M: sensitive scalar suppressed before AI\n");
        result.append("  \"p:*\": run-local pseudonym preserving equality only\n");
        result.append("documents:\n");
        for (var document : documents) {
            appendDocument(result, document);
        }
        return result.toString();
    }

    private void appendDocument(StringBuilder result, SanitizedConfigurationDocument document) {
        result.append("  - meta: [")
                .append(yamlScalar(document.role() != null ? document.role().name() : null))
                .append(", ").append(document.documentIndex())
                .append(", ").append(yamlScalar(document.sourcePath()))
                .append(", ").append(yamlScalar(document.targetPath()))
                .append(", ").append(document.sourcePresent())
                .append(", ").append(document.targetPresent())
                .append(", ").append(yamlScalar(document.sourceProfileToken()))
                .append(", ").append(yamlScalar(document.targetProfileToken()))
                .append("]\n");
        if (document.root() == null) {
            result.append("    tree: []\n");
            return;
        }
        result.append("    tree:\n");
        appendNode(result, document.root(), 6);
    }

    private void appendNode(StringBuilder result, SanitizedConfigurationNode node, int indent) {
        result.append(" ".repeat(indent)).append("- n: [")
                .append(yamlScalar(node.name()))
                .append(", ").append(yamlScalar(relationCode(node.relation())))
                .append(", ").append(yamlScalar(typeCode(node.sourceType())))
                .append(", ").append(yamlScalar(typeCode(node.targetType())))
                .append(", ").append(yamlScalar(sensitivityCode(node.sensitivity())))
                .append(", ").append(yamlScalar(representation(
                        node.sourceType(),
                        node.sensitivity(),
                        node.sourceValueToken()
                )))
                .append(", ").append(yamlScalar(representation(
                        node.targetType(),
                        node.sensitivity(),
                        node.targetValueToken()
                )))
                .append(", ").append(number(node.sourceCardinality()))
                .append(", ").append(number(node.targetCardinality()))
                .append("]\n");
        if (!node.children().isEmpty()) {
            result.append(" ".repeat(indent + 2)).append("c:\n");
            for (var child : node.children()) {
                appendNode(result, child, indent + 4);
            }
        }
    }

    private Map<String, Object> changes(RuntimeConfigurationDeterministicContext context) {
        var result = new LinkedHashMap<String, Object>();
        result.put("formatVersion", 1);
        result.put("deterministicStatus", context.status());
        result.put("codes", codes());
        result.put("differenceColumns", List.of(
                "differenceId",
                "role",
                "documentIndex",
                "path",
                "kind",
                "sourceType",
                "targetType",
                "sensitivity",
                "sourceRepresentation",
                "targetRepresentation"
        ));
        result.put(
                "differences",
                context.differences().stream().map(this::compactDifference).toList()
        );
        result.put("findingColumns", List.of(
                "findingId", "code", "severity", "path", "differenceIds", "referenceIds"
        ));
        result.put(
                "findings",
                context.findings().stream()
                        .map(finding -> List.of(
                                value(finding.findingId()),
                                value(finding.code()),
                                value(finding.severity()),
                                value(finding.path()),
                                finding.differenceIds(),
                                finding.referenceIds()
                        ))
                        .toList()
        );
        result.put("referenceColumns", List.of(
                "referenceId",
                "sourceRole",
                "documentIndex",
                "sourcePath",
                "targetPath",
                "referenceKind",
                "sourceStatus",
                "targetStatus"
        ));
        result.put(
                "references",
                context.references().stream()
                        .map(reference -> List.of(
                                value(reference.referenceId()),
                                value(reference.sourceRole()),
                                reference.documentIndex(),
                                value(reference.sourcePath()),
                                value(reference.targetPath()),
                                value(reference.referenceKind()),
                                value(reference.sourceStatus()),
                                value(reference.targetStatus())
                        ))
                        .toList()
        );
        return result;
    }

    private List<Object> compactDifference(RuntimeConfigurationDifference difference) {
        var row = new ArrayList<Object>(10);
        row.add(value(difference.differenceId()));
        row.add(value(difference.role()));
        row.add(difference.documentIndex());
        row.add(value(difference.path()));
        row.add(relationCode(difference.kind()));
        row.add(typeCode(difference.sourceType()));
        row.add(typeCode(difference.targetType()));
        row.add(sensitivityCode(difference.sensitivity()));
        row.add(representation(
                difference.sourceType(),
                difference.sensitivity(),
                difference.sourceValueToken()
        ));
        row.add(representation(
                difference.targetType(),
                difference.sensitivity(),
                difference.targetValueToken()
        ));
        return Collections.unmodifiableList(row);
    }

    private String representation(
            RuntimeConfigurationValueType type,
            RuntimeConfigurationSensitivity sensitivity,
            String token
    ) {
        if (type == null) {
            return "A";
        }
        if (sensitivity == RuntimeConfigurationSensitivity.SENSITIVE) {
            return "M";
        }
        return token != null ? "p:" + token : "O";
    }

    private Map<String, Object> codes() {
        var result = new LinkedHashMap<String, Object>();
        result.put("relation", Map.of(
                "U", "UNCHANGED",
                "A", "ADDED",
                "R", "REMOVED",
                "C", "CHANGED",
                "T", "TYPE_CHANGED",
                "E", "EFFECTIVE_CHANGED"
        ));
        result.put("type", Map.of(
                "M", "MAP",
                "L", "LIST",
                "S", "STRING",
                "N", "NUMBER",
                "B", "BOOLEAN",
                "Z", "NULL",
                "X", "UNKNOWN"
        ));
        result.put("sensitivity", Map.of("N", "NON_SENSITIVE", "S", "SENSITIVE"));
        result.put("representation", Map.of(
                "A", "ABSENT",
                "O", "STRUCTURE_ONLY",
                "M", "SENSITIVE_MASKED",
                "p:*", "RUN_LOCAL_PSEUDONYM"
        ));
        return result;
    }

    private String relationCode(
            pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                    .RuntimeConfigurationChangeKind value
    ) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case UNCHANGED -> "U";
            case ADDED -> "A";
            case REMOVED -> "R";
            case CHANGED -> "C";
            case TYPE_CHANGED -> "T";
            case EFFECTIVE_CHANGED -> "E";
        };
    }

    private String typeCode(RuntimeConfigurationValueType value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case MAP -> "M";
            case LIST -> "L";
            case STRING -> "S";
            case NUMBER -> "N";
            case BOOLEAN -> "B";
            case NULL -> "Z";
            case UNKNOWN -> "X";
        };
    }

    private String sensitivityCode(RuntimeConfigurationSensitivity value) {
        if (value == null) {
            return null;
        }
        return value == RuntimeConfigurationSensitivity.SENSITIVE ? "S" : "N";
    }

    private String boundedYaml(String content, List<String> visibilityLimits) {
        if (content.length() <= MAX_STRUCTURE_CHARACTERS) {
            return content;
        }
        visibilityLimits.add(
                "Compact configuration tree was truncated for the AI context; "
                        + "deterministic comparison remains complete."
        );
        var limit = Math.max(0, MAX_STRUCTURE_CHARACTERS - YAML_TRUNCATION_MARKER.length());
        var boundary = content.lastIndexOf('\n', Math.min(content.length() - 1, limit));
        var safeBoundary = Math.max(0, boundary + 1);
        return content.substring(0, safeBoundary) + YAML_TRUNCATION_MARKER;
    }

    private String boundedJson(
            Object value,
            int maxCharacters,
            String label,
            List<String> visibilityLimits
    ) {
        var rendered = json(value);
        if (rendered.length() <= maxCharacters) {
            return rendered;
        }
        visibilityLimits.add("AI artifact `" + label
                + "` was truncated; the immutable deterministic result remains available.");
        var prefixLength = Math.min(rendered.length(), Math.max(0, maxCharacters / 2));
        String result;
        do {
            result = json(Map.of(
                    "formatVersion", 1,
                    "truncated", true,
                    "reason", "sanitized AI artifact character limit reached",
                    "originalCharacterCount", rendered.length(),
                    "sanitizedContentPrefix", rendered.substring(0, prefixLength)
            ));
            prefixLength = Math.max(0, prefixLength - Math.max(1, result.length() - maxCharacters));
        } while (result.length() > maxCharacters && prefixLength > 0);
        return result;
    }

    private String yamlScalar(Object value) {
        return value != null ? json(String.valueOf(value)) : "null";
    }

    private String number(Integer value) {
        return value != null ? value.toString() : "null";
    }

    private Object value(Object value) {
        return value != null ? value : "";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Sanitized AI artifact could not be rendered.", exception);
        }
    }

    private String responseContract() {
        return """
                {
                  "conclusion": "NO_CONCERN | REVIEW_REQUIRED | LIKELY_CONFIGURATION_ERROR | INCONCLUSIVE",
                  "confidence": "LOW | MEDIUM | HIGH",
                  "summary": "druga opinia bez powtarzania diffu",
                  "observations": [{
                    "observationId": "ai-observation-1",
                    "type": "GROUNDED_OBSERVATION | HYPOTHESIS",
                    "summary": "co to oznacza",
                    "explanation": "dlaczego",
                    "differenceIds": [],
                    "findingIds": [],
                    "contextIds": [],
                    "codeGroundingIds": []
                  }],
                  "recommendedHumanChecks": [],
                  "functionalImpacts": [{
                    "impactId": "functional-impact-1",
                    "affectedFunctionality": "nazwa funkcjonalności",
                    "impact": "opis wpływu",
                    "confidence": "LOW | MEDIUM | HIGH",
                    "hypothesis": false,
                    "systemIds": [],
                    "differenceIds": [],
                    "findingIds": [],
                    "contextIds": [],
                    "codeGroundingIds": []
                  }],
                  "visibilityLimits": []
                }
                """.trim();
    }

    public record RuntimeConfigurationAiArtifacts(
            Map<String, String> contents,
            List<String> visibilityLimits
    ) {

        public RuntimeConfigurationAiArtifacts {
            contents = contents != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(contents))
                    : Map.of();
            visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        }
    }
}
