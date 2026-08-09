package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingSeverity;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVerificationCheckResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChangeVerificationAiResponseParser {

    private final ObjectMapper objectMapper;

    public ChangeVerificationAiResponse parse(String content) {
        var json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            return fallback("AI response did not contain JSON compliance result.");
        }

        try {
            var root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                return fallback("AI response JSON was not an object.");
            }
            if (!StringUtils.hasText(text(root, "status"))) {
                return fallback("AI response did not contain compliance status.");
            }
            var checksNode = requiredArray(root, "verificationChecks");
            var findingsNode = requiredArray(root, "findings");
            var suggestedActions = requiredTextList(root, "suggestedActions");
            var visibilityLimits = requiredTextList(root, "visibilityLimits");
            if (checksNode == null || findingsNode == null
                    || suggestedActions == null || visibilityLimits == null) {
                return fallback("AI response did not satisfy the required collection contract.");
            }

            var parsedChecks = parseChecks(checksNode);
            var findings = parseFindings(findingsNode);
            if (findings == null) {
                return fallback("AI response contained an invalid finding.");
            }
            var mergedVisibilityLimits = new ArrayList<>(visibilityLimits);
            mergedVisibilityLimits.addAll(parsedChecks.visibilityLimits());

            return new ChangeVerificationAiResponse(
                    text(root, "status"),
                    parsedChecks.checks(),
                    findings,
                    suggestedActions,
                    mergedVisibilityLimits,
                    text(root, "confidence")
            );
        } catch (JsonProcessingException exception) {
            return fallback("AI response JSON could not be parsed: " + exception.getMessage());
        }
    }

    private ParsedChecks parseChecks(JsonNode checksNode) {
        var checks = new ArrayList<ChangeVerificationVerificationCheckResponse>();
        var visibilityLimits = new ArrayList<String>();
        for (var index = 0; index < checksNode.size(); index++) {
            var node = checksNode.get(index);
            var parsed = parseCheck(node);
            if (parsed == null) {
                visibilityLimits.add("AI response verification check at index " + index
                        + " was ignored because its field types were invalid.");
                continue;
            }
            var validationError = validationError(parsed);
            if (StringUtils.hasText(validationError)) {
                var checkLabel = StringUtils.hasText(parsed.id()) ? "`" + parsed.id() + "`" : "at index " + index;
                visibilityLimits.add("AI response verification check " + checkLabel
                        + " was ignored: " + validationError);
                continue;
            }
            checks.add(parsed);
        }
        return new ParsedChecks(List.copyOf(checks), List.copyOf(visibilityLimits));
    }

    private ChangeVerificationVerificationCheckResponse parseCheck(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var inferenceSignals = optionalTextList(node.get("inferenceSignals"));
        var evidenceRefs = optionalTextList(node.get("evidenceRefs"));
        var gaps = optionalTextList(node.get("gaps"));
        if (inferenceSignals == null || evidenceRefs == null || gaps == null) {
            return null;
        }
        return new ChangeVerificationVerificationCheckResponse(
                text(node, "id"),
                text(node, "origin"),
                text(node, "scope"),
                text(node, "criterionSource"),
                text(node, "criterionQuote"),
                text(node, "interpretationType"),
                text(node, "criticality"),
                text(node, "inferenceRationale"),
                inferenceSignals,
                text(node, "riskIfOmitted"),
                text(node, "confidence"),
                text(node, "expectedCriterion"),
                text(node, "verificationStatus"),
                text(node, "verifiedAgainst"),
                text(node, "analysis"),
                evidenceRefs,
                gaps,
                text(node, "suggestedAction")
        );
    }

    private String validationError(ChangeVerificationVerificationCheckResponse check) {
        var allowedOrigins = Set.of("DEFINED", "INFERRED_CRITICAL");
        var allowedDefinedScopes = Set.of("STORY_COMPLIANCE", "INSTRUCTION_COMPLIANCE");
        var allowedStatuses = Set.of("PASSED", "WARNING", "FAILED", "NOT_VERIFIED");
        if (check == null || !StringUtils.hasText(check.id())) {
            return "verification check did not contain id.";
        }
        var origin = normalized(check.origin());
        if (!allowedOrigins.contains(origin)) {
            return "verification check did not contain a supported origin.";
        }
        if ("DEFINED".equals(origin) && !allowedDefinedScopes.contains(normalized(check.scope()))) {
            return "defined verification check contained an unsupported scope.";
        }
        if (!allowedStatuses.contains(normalized(check.verificationStatus()))) {
            return "verification check contained an unsupported status.";
        }
        if ("INFERRED_CRITICAL".equals(origin)) {
            if (!"INFERRED_CRITICAL_CHECKS".equals(normalized(check.scope()))) {
                return "inferred critical check contained an unsupported scope.";
            }
            if (!StringUtils.hasText(check.criticality())
                    || !StringUtils.hasText(check.inferenceRationale())
                    || check.inferenceSignals().isEmpty()
                    || !StringUtils.hasText(check.riskIfOmitted())
                    || !StringUtils.hasText(check.confidence())) {
                return "inferred critical check was incomplete.";
            }
            if (!Set.of("HIGH", "BLOCKER").contains(normalized(check.criticality()))
                    || !Set.of("HIGH", "MEDIUM", "LOW").contains(normalized(check.confidence()))) {
                return "inferred critical metadata was outside the supported contract.";
            }
        }
        return null;
    }

    private List<ChangeVerificationFindingResponse> parseFindings(JsonNode findingsNode) {
        var findings = new ArrayList<ChangeVerificationFindingResponse>();
        for (var node : findingsNode) {
            if (node == null || !node.isObject()) {
                return null;
            }
            var severity = severity(text(node, "severity"));
            var references = optionalTextList(node.get("references"));
            if (severity == null || references == null) {
                return null;
            }
            findings.add(new ChangeVerificationFindingResponse(
                    text(node, "id"),
                    severity,
                    text(node, "source"),
                    text(node, "summary"),
                    text(node, "details"),
                    references,
                    text(node, "suggestedAction")
            ));
        }
        return List.copyOf(findings);
    }

    private JsonNode requiredArray(JsonNode root, String fieldName) {
        var node = root.get(fieldName);
        return node != null && node.isArray() ? node : null;
    }

    private List<String> requiredTextList(JsonNode root, String fieldName) {
        var node = requiredArray(root, fieldName);
        return node != null ? optionalTextList(node) : null;
    }

    private List<String> optionalTextList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return null;
        }
        var values = new ArrayList<String>();
        for (var item : node) {
            if (!item.isTextual()) {
                return null;
            }
            values.add(item.asText());
        }
        return List.copyOf(values);
    }

    private String text(JsonNode node, String fieldName) {
        var value = node != null ? node.get(fieldName) : null;
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private ChangeVerificationFindingSeverity severity(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ChangeVerificationFindingSeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(java.util.Locale.ROOT) : "";
    }

    public ChangeVerificationAiResponse fallback(String limitation) {
        return new ChangeVerificationAiResponse(
                "INCONCLUSIVE",
                List.of(),
                List.of(),
                List.of("Run compliance verification again or inspect collected evidence manually."),
                List.of(limitation),
                "low"
        );
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        var trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        var fenceStart = trimmed.indexOf("```json");
        if (fenceStart >= 0) {
            var jsonStart = trimmed.indexOf('\n', fenceStart);
            var fenceEnd = trimmed.indexOf("```", jsonStart + 1);
            if (jsonStart >= 0 && fenceEnd > jsonStart) {
                return trimmed.substring(jsonStart + 1, fenceEnd).trim();
            }
        }

        var objectStart = trimmed.indexOf('{');
        var objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1).trim();
        }

        return null;
    }

    private record ParsedChecks(
            List<ChangeVerificationVerificationCheckResponse> checks,
            List<String> visibilityLimits
    ) {
    }
}
