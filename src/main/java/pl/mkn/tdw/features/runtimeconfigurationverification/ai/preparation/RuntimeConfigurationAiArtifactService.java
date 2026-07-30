package pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationAiArtifactService {

    static final int MAX_ARTIFACT_CHARACTERS = 120_000;
    static final int MAX_MANIFEST_CHARACTERS = 360_000;
    private static final String TRUNCATION_MARKER =
            "\n\n{\"truncated\":true,\"reason\":\"sanitized AI artifact character limit reached\"}";

    private final ObjectMapper objectMapper;

    public RuntimeConfigurationAiArtifacts render(
            RuntimeConfigurationVerificationMode mode,
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deepContext
    ) {
        if (deterministic == null) {
            throw new IllegalArgumentException("deterministic context is required");
        }
        var artifacts = new LinkedHashMap<String, String>();
        var visibilityLimits = new ArrayList<String>();
        artifacts.put("runtime-configuration/scope.json", json(scope(deterministic, mode)));
        artifacts.put("runtime-configuration/coverage.json", boundedJson(Map.of(
                "source", deterministic.sourceCoverage(),
                "target", deterministic.targetCoverage()
        ), "coverage", visibilityLimits));
        artifacts.put("runtime-configuration/differences-and-findings.json", boundedJson(Map.of(
                "deterministicStatus", deterministic.status(),
                "differences", deterministic.differences().stream().map(this::safeDifference).toList(),
                "findings", deterministic.findings(),
                "references", deterministic.references()
        ), "differences-and-findings", visibilityLimits));

        var manifestCharacters = 0;
        for (var document : deterministic.documents()) {
            var artifactName = "runtime-configuration/manifest/"
                    + document.role().name().toLowerCase()
                    + "-document-" + document.documentIndex() + ".json";
            var rendered = json(safeDocument(document));
            var remaining = Math.max(0, MAX_MANIFEST_CHARACTERS - manifestCharacters);
            if (rendered.length() > MAX_ARTIFACT_CHARACTERS || rendered.length() > remaining) {
                var limit = Math.min(MAX_ARTIFACT_CHARACTERS, remaining);
                rendered = truncate(rendered, limit);
                visibilityLimits.add("Sanitized manifest group `" + artifactName
                        + "` was truncated for the AI context; deterministic comparison remains complete.");
            }
            artifacts.put(artifactName, rendered);
            manifestCharacters += Math.min(rendered.length(), remaining);
        }
        artifacts.put("runtime-configuration/manifest-index.json", json(Map.of(
                "documentCount", deterministic.documents().size(),
                "includesChangedAndUnchangedParameters", true,
                "preservesYamlDocumentAndProfileBoundaries", true,
                "artifactNames", artifacts.keySet().stream()
                        .filter(name -> name.startsWith("runtime-configuration/manifest/"))
                        .toList()
        )));

        if (mode == RuntimeConfigurationVerificationMode.DEEP && deepContext != null) {
            artifacts.put(
                    "runtime-configuration/deep-context.json",
                    boundedJson(deepContext, "deep-context", visibilityLimits)
            );
            visibilityLimits.addAll(deepContext.visibilityLimits());
        }
        artifacts.put("runtime-configuration/response-contract.json", responseContract());
        return new RuntimeConfigurationAiArtifacts(artifacts, visibilityLimits);
    }

    private Map<String, Object> scope(
            RuntimeConfigurationDeterministicContext context,
            RuntimeConfigurationVerificationMode mode
    ) {
        var result = new LinkedHashMap<String, Object>();
        result.put("mode", mode);
        result.put("repositoryId", context.repositoryId());
        result.put("systemId", context.systemId());
        result.put("systemLabel", context.systemLabel());
        result.put("configurationDirectory", context.configurationDirectory());
        result.put("sourceBranch", context.sourceBranch());
        result.put("targetBranch", context.targetBranch());
        return result;
    }

    private Map<String, Object> safeDocument(SanitizedConfigurationDocument document) {
        var result = new LinkedHashMap<String, Object>();
        result.put("role", document.role());
        result.put("sourcePath", document.sourcePath());
        result.put("targetPath", document.targetPath());
        result.put("documentIndex", document.documentIndex());
        result.put("sourcePresent", document.sourcePresent());
        result.put("targetPresent", document.targetPresent());
        result.put("sourceProfileToken", document.sourceProfileToken());
        result.put("targetProfileToken", document.targetProfileToken());
        result.put("root", safeNode(document.root()));
        return result;
    }

    private Object safeNode(SanitizedConfigurationNode node) {
        if (node == null) {
            return null;
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("name", node.name());
        result.put("path", node.path());
        result.put("sourceType", node.sourceType());
        result.put("targetType", node.targetType());
        result.put("relation", node.relation());
        result.put("sensitivity", node.sensitivity());
        if (node.sensitivity() == RuntimeConfigurationSensitivity.SENSITIVE) {
            result.put("sourceValue", node.sourceType() != null ? "MASKED" : null);
            result.put("targetValue", node.targetType() != null ? "MASKED" : null);
        } else {
            result.put("sourceValueToken", node.sourceValueToken());
            result.put("targetValueToken", node.targetValueToken());
        }
        result.put("sourceCardinality", node.sourceCardinality());
        result.put("targetCardinality", node.targetCardinality());
        result.put("children", node.children().stream().map(this::safeNode).toList());
        return result;
    }

    private Map<String, Object> safeDifference(
            pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                    .RuntimeConfigurationDifference difference
    ) {
        var result = new LinkedHashMap<String, Object>();
        result.put("differenceId", difference.differenceId());
        result.put("role", difference.role());
        result.put("documentIndex", difference.documentIndex());
        result.put("path", difference.path());
        result.put("kind", difference.kind());
        result.put("sourceType", difference.sourceType());
        result.put("targetType", difference.targetType());
        result.put("sensitivity", difference.sensitivity());
        if (difference.sensitivity() == RuntimeConfigurationSensitivity.SENSITIVE) {
            result.put("sourceValue", difference.sourceType() != null ? "MASKED" : null);
            result.put("targetValue", difference.targetType() != null ? "MASKED" : null);
        } else {
            result.put("sourceValueToken", difference.sourceValueToken());
            result.put("targetValueToken", difference.targetValueToken());
        }
        return result;
    }

    private String boundedJson(Object value, String label, List<String> visibilityLimits) {
        var rendered = json(value);
        if (rendered.length() <= MAX_ARTIFACT_CHARACTERS) {
            return rendered;
        }
        visibilityLimits.add("AI artifact `" + label
                + "` was truncated; the immutable deterministic result remains available.");
        return truncate(rendered, MAX_ARTIFACT_CHARACTERS);
    }

    private String truncate(String value, int maxCharacters) {
        if (maxCharacters <= TRUNCATION_MARKER.length()) {
            return TRUNCATION_MARKER.trim();
        }
        return value.substring(0, Math.min(value.length(), maxCharacters - TRUNCATION_MARKER.length()))
                + TRUNCATION_MARKER;
    }

    private String json(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
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
