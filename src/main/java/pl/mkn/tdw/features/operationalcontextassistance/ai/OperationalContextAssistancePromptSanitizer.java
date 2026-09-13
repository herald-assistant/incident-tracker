package pl.mkn.tdw.features.operationalcontextassistance.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.regex.Pattern;

public final class OperationalContextAssistancePromptSanitizer {

    static final String REDACTION_LIMIT = "Pominięto wrażliwe fragmenty opisu lub źródła przed przekazaniem do AI.";

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)(?:password|passwd|secret|token|authorization|api[_\\-.]?key|private[_\\-.]?key|client[_\\-.]?secret)"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(?:password|passwd|secret|access[_\\-.]?token|refresh[_\\-.]?token|api[_\\-.]?key|authorization|client[_\\-.]?secret)\\s*[:=]\\s*\\S+"
    );
    private static final Pattern TOKEN_VALUE = Pattern.compile(
            "(?i)(?:bearer\\s+\\S+|glpat-[A-Za-z0-9_-]+|gh[pousr]_[A-Za-z0-9_]+|sk-[A-Za-z0-9_-]{16,})"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"
    );
    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "(?is)-----BEGIN [^-]*PRIVATE KEY-----"
    );

    private final ObjectMapper objectMapper;

    OperationalContextAssistancePromptSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static boolean containsSensitiveContent(JsonNode value, ObjectMapper objectMapper) {
        var limits = new java.util.LinkedHashSet<String>();
        new OperationalContextAssistancePromptSanitizer(objectMapper).sanitize(value, limits);
        return limits.contains(REDACTION_LIMIT);
    }

    JsonNode sanitize(JsonNode value, Set<String> visibilityLimits) {
        if (value == null || value.isNull()) {
            return objectMapper.nullNode();
        }
        if (value.isObject()) {
            ObjectNode safe = objectMapper.createObjectNode();
            value.fields().forEachRemaining(field -> {
                if (SENSITIVE_KEY.matcher(field.getKey()).find()) {
                    safe.put(field.getKey(), "[pominięto wrażliwą wartość]");
                    visibilityLimits.add(REDACTION_LIMIT);
                } else {
                    safe.set(field.getKey(), sanitize(field.getValue(), visibilityLimits));
                }
            });
            return safe;
        }
        if (value.isArray()) {
            ArrayNode safe = objectMapper.createArrayNode();
            value.forEach(element -> safe.add(sanitize(element, visibilityLimits)));
            return safe;
        }
        if (value.isTextual()) {
            return objectMapper.getNodeFactory().textNode(sanitize(value.textValue(), visibilityLimits));
        }
        return value.deepCopy();
    }

    String sanitize(String value, Set<String> visibilityLimits) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (PRIVATE_KEY_BLOCK.matcher(value).find()) {
            visibilityLimits.add(REDACTION_LIMIT);
            return "[pominięto blok klucza prywatnego]";
        }
        var safe = new StringBuilder();
        for (String line : value.split("\\R", -1)) {
            if (SENSITIVE_ASSIGNMENT.matcher(line).find() || TOKEN_VALUE.matcher(line).find()) {
                safe.append("[pominięto wrażliwą linię]");
                visibilityLimits.add(REDACTION_LIMIT);
            } else {
                var withoutEmail = EMAIL.matcher(line).replaceAll("[adres e-mail pominięty]");
                if (!withoutEmail.equals(line)) {
                    visibilityLimits.add(REDACTION_LIMIT);
                }
                safe.append(withoutEmail);
            }
            safe.append('\n');
        }
        return safe.toString().stripTrailing();
    }
}
