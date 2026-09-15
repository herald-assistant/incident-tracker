package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationInterpretationType;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationReleaseImpact;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleEvidenceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleOutcome;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleScope;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleSourceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleSourceType;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVisibilityLimitResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ChangeVerificationAiResponseParser {

    private final ObjectMapper objectMapper;

    public ChangeVerificationAiResponse parse(String content) {
        var json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            return fallback("AI response did not contain a JSON rule ledger.");
        }

        try {
            var root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                return fallback("AI response JSON was not an object.");
            }
            var rulesNode = requiredArray(root, "rules");
            var additionalChecksNode = requiredArray(root, "additionalChecks");
            var visibilityLimitsNode = requiredArray(root, "visibilityLimits");
            if (rulesNode == null || additionalChecksNode == null || visibilityLimitsNode == null) {
                return fallback("AI response did not satisfy the required rule ledger collections.");
            }

            var rules = parseRules(rulesNode, false);
            var additionalChecks = parseRules(additionalChecksNode, true);
            if (rules == null || additionalChecks == null || hasDuplicateIds(rules, additionalChecks)) {
                return fallback("AI response contained an invalid or duplicate rule.");
            }
            if (additionalChecks.size() > 5) {
                return fallback("AI response contained more than five additional checks.");
            }
            var visibilityLimits = parseVisibilityLimits(visibilityLimitsNode, rules, additionalChecks);
            if (visibilityLimits == null) {
                return fallback("AI response contained an invalid or unlinked visibility limit.");
            }
            return new ChangeVerificationAiResponse(rules, additionalChecks, visibilityLimits);
        } catch (JsonProcessingException exception) {
            return fallback("AI response JSON could not be parsed: " + exception.getMessage());
        }
    }

    private List<ChangeVerificationRuleResultResponse> parseRules(JsonNode array, boolean additional) {
        var rules = new ArrayList<ChangeVerificationRuleResultResponse>();
        for (var node : array) {
            var rule = parseRule(node);
            if (rule == null || !validRule(rule, additional)) {
                return null;
            }
            rules.add(rule);
        }
        return List.copyOf(rules);
    }

    private ChangeVerificationRuleResultResponse parseRule(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var source = parseSource(node.get("source"));
        var evidence = parseEvidence(requiredArray(node, "evidence"));
        var missingEvidence = textList(requiredArray(node, "missingEvidence"));
        var signals = textList(requiredArray(node, "signals"));
        if (source == null || evidence == null || missingEvidence == null || signals == null) {
            return null;
        }
        return new ChangeVerificationRuleResultResponse(
                text(node, "id"),
                enumValue(ChangeVerificationRuleScope.class, text(node, "scope")),
                source,
                text(node, "normalizedRule"),
                enumValue(ChangeVerificationInterpretationType.class, text(node, "interpretationType")),
                enumValue(ChangeVerificationRuleOutcome.class, text(node, "outcome")),
                enumValue(ChangeVerificationReleaseImpact.class, text(node, "releaseImpact")),
                text(node, "conclusion"),
                evidence,
                missingEvidence,
                text(node, "action"),
                text(node, "rationale"),
                text(node, "riskIfOmitted"),
                signals,
                text(node, "confidence")
        );
    }

    private ChangeVerificationRuleSourceResponse parseSource(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var type = enumValue(ChangeVerificationRuleSourceType.class, text(node, "type"));
        var label = text(node, "label");
        var reference = text(node, "reference");
        var quote = text(node, "quote");
        if (type == null || !hasText(label) || !hasText(reference) || !hasText(quote)) {
            return null;
        }
        return new ChangeVerificationRuleSourceResponse(type, label, reference, quote);
    }

    private List<ChangeVerificationRuleEvidenceResponse> parseEvidence(JsonNode array) {
        if (array == null) {
            return null;
        }
        var evidence = new ArrayList<ChangeVerificationRuleEvidenceResponse>();
        for (var node : array) {
            if (node == null || !node.isObject()) {
                return null;
            }
            var summary = text(node, "summary");
            var reference = text(node, "reference");
            if (!hasText(summary) || !hasText(reference)) {
                return null;
            }
            evidence.add(new ChangeVerificationRuleEvidenceResponse(summary, reference));
        }
        return List.copyOf(evidence);
    }

    private boolean validRule(ChangeVerificationRuleResultResponse rule, boolean additional) {
        if (!hasText(rule.id()) || rule.scope() == null || rule.source() == null
                || !hasText(rule.normalizedRule()) || rule.interpretationType() == null
                || rule.outcome() == null || rule.releaseImpact() == null
                || !hasText(rule.conclusion())) {
            return false;
        }
        if (additional) {
            if (rule.scope() != ChangeVerificationRuleScope.ADDITIONAL
                    || rule.source().type() != ChangeVerificationRuleSourceType.AI_SUGGESTION
                    || rule.interpretationType() != ChangeVerificationInterpretationType.INFERRED
                    || !hasText(rule.rationale())
                    || !hasText(rule.riskIfOmitted())
                    || rule.signals().isEmpty()
                    || !List.of("HIGH", "MEDIUM", "LOW").contains(normalized(rule.confidence()))) {
                return false;
            }
        } else if (rule.scope() == ChangeVerificationRuleScope.ADDITIONAL
                || rule.source().type() == ChangeVerificationRuleSourceType.AI_SUGGESTION
                || rule.interpretationType() == ChangeVerificationInterpretationType.INFERRED) {
            return false;
        }
        if (rule.outcome() == ChangeVerificationRuleOutcome.SATISFIED) {
            return !rule.evidence().isEmpty() && rule.releaseImpact() == ChangeVerificationReleaseImpact.NONE;
        }
        return hasText(rule.action())
                && (rule.outcome() != ChangeVerificationRuleOutcome.NOT_VERIFIED
                || !rule.missingEvidence().isEmpty());
    }

    private List<ChangeVerificationVisibilityLimitResponse> parseVisibilityLimits(
            JsonNode array,
            List<ChangeVerificationRuleResultResponse> rules,
            List<ChangeVerificationRuleResultResponse> additionalChecks
    ) {
        var knownIds = new HashSet<String>();
        rules.forEach(rule -> knownIds.add(rule.id()));
        additionalChecks.forEach(rule -> knownIds.add(rule.id()));
        var limits = new ArrayList<ChangeVerificationVisibilityLimitResponse>();
        for (var node : array) {
            if (node == null || !node.isObject()) {
                return null;
            }
            var message = text(node, "message");
            var ids = textList(requiredArray(node, "affectedRuleIds"));
            if (!hasText(message) || ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> !knownIds.contains(id))) {
                return null;
            }
            limits.add(new ChangeVerificationVisibilityLimitResponse(message, ids));
        }
        return List.copyOf(limits);
    }

    private boolean hasDuplicateIds(
            List<ChangeVerificationRuleResultResponse> rules,
            List<ChangeVerificationRuleResultResponse> additionalChecks
    ) {
        var ids = new HashSet<String>();
        return java.util.stream.Stream.concat(rules.stream(), additionalChecks.stream())
                .map(ChangeVerificationRuleResultResponse::id)
                .anyMatch(id -> !ids.add(id));
    }

    private JsonNode requiredArray(JsonNode node, String fieldName) {
        var value = node != null ? node.get(fieldName) : null;
        return value != null && value.isArray() ? value : null;
    }

    private List<String> textList(JsonNode array) {
        if (array == null) {
            return null;
        }
        var values = new ArrayList<String>();
        for (var item : array) {
            if (!item.isTextual() || !hasText(item.asText())) {
                return null;
            }
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }

    private String text(JsonNode node, String fieldName) {
        var value = node != null ? node.get(fieldName) : null;
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, normalized(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public ChangeVerificationAiResponse fallback(String limitation) {
        return new ChangeVerificationAiResponse(
                List.of(),
                List.of(),
                List.of(new ChangeVerificationVisibilityLimitResponse(limitation, List.of()))
        );
    }

    private String extractJson(String content) {
        if (!hasText(content)) {
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
        return objectStart >= 0 && objectEnd > objectStart
                ? trimmed.substring(objectStart, objectEnd + 1).trim()
                : null;
    }

    private String normalized(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
