package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import java.math.BigDecimal;
import java.util.Set;

/** The deliberately small executable schema subset, shared by import and execution. */
public final class OpenApiSchema {
    private OpenApiSchema() {}
    private static final Set<String> FIELDS = Set.of("type", "description", "title", "properties", "items",
            "required", "enum", "additionalProperties", "minimum", "maximum", "minLength", "maxLength",
            "minItems", "maxItems", "example", "default", "readOnly", "format", "nullable");

    public static void check(JsonNode schema, String pointer, int depth) {
        if (depth > 20 || !schema.isObject()) fail(pointer, "Schemat musi być obiektem o głębokości do 20.");
        schema.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field)) fail(pointer + "/" + field, "Nieobsługiwane pole schematu.");
        });
        for (var flag : Set.of("readOnly", "nullable"))
            if (schema.has(flag) && !schema.get(flag).isBoolean()) fail(pointer + "/" + flag, flag + " musi być boolean.");
        if (schema.has("format") && !schema.get("format").isTextual()) fail(pointer + "/format", "format musi być string.");
        var type = schema.path("type").asText();
        if (!Set.of("object", "array", "string", "number", "integer", "boolean").contains(type))
            fail(pointer, "Wymagany jawny obsługiwany type.");
        if (schema.has("additionalProperties") && !schema.get("additionalProperties").isBoolean())
            fail(pointer, "additionalProperties musi być boolean.");
        if (schema.has("enum") && (!schema.get("enum").isArray() || schema.get("enum").isEmpty()))
            fail(pointer, "enum musi być niepustą tablicą.");
        for (var key : Set.of("minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems")) {
            if (schema.has(key) && (!schema.get(key).isNumber()
                    || (!key.equals("minimum") && !key.equals("maximum") &&
                    (!schema.get(key).isIntegralNumber() || schema.get(key).asLong() < 0))))
                fail(pointer + "/" + key, "Nieprawidłowe ograniczenie.");
        }
        if (schema.has("minimum") && schema.has("maximum") &&
                schema.get("minimum").decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0)
            fail(pointer, "minimum > maximum.");
        for (var pair : new String[][]{{"minLength", "maxLength"}, {"minItems", "maxItems"}})
            if (schema.has(pair[0]) && schema.has(pair[1]) && schema.get(pair[0]).asLong() > schema.get(pair[1]).asLong())
                fail(pointer, "Sprzeczne ograniczenia.");
        if (type.equals("object")) {
            var properties = schema.path("properties");
            if (!properties.isMissingNode() && !properties.isObject()) fail(pointer, "properties musi być obiektem.");
            properties.fields().forEachRemaining(entry -> check(entry.getValue(), pointer + "/properties/" + entry.getKey(), depth + 1));
            if (schema.has("required")) {
                if (!schema.get("required").isArray()) fail(pointer, "required musi być tablicą.");
                var seen = new java.util.HashSet<String>();
                schema.get("required").forEach(value -> {
                    if (!value.isTextual() || !properties.has(value.asText()) || !seen.add(value.asText()))
                        fail(pointer, "required wskazuje nieznane lub powtórzone pole.");
                });
            }
        } else if (type.equals("array")) {
            check(schema.path("items"), pointer + "/items", depth + 1);
        }
        if (!type.equals("object") && (schema.has("properties") || schema.has("required") || schema.has("additionalProperties")))
            fail(pointer, "Pola obiektu na innym type.");
        if (!type.equals("array") && schema.has("items")) fail(pointer, "items poza array.");
    }

    public static void validate(JsonNode schema, JsonNode value, String pointer) {
        if (value == null) fail(pointer, "Wymagany JSON argumentów.");
        var type = schema.path("type").asText();
        boolean valid = switch (type) {
            case "object" -> value.isObject(); case "array" -> value.isArray();
            case "string" -> value.isTextual(); case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber(); case "boolean" -> value.isBoolean();
            default -> false;
        };
        if (!valid && !(value.isNull() && schema.path("nullable").asBoolean())) fail(pointer, "Oczekiwany typ: " + type);
        if (schema.has("enum")) {
            boolean match = false;
            for (var candidate : schema.get("enum"))
                match |= candidate.equals(value) || candidate.isNumber() && value.isNumber()
                        && candidate.decimalValue().compareTo(value.decimalValue()) == 0;
            if (!match) fail(pointer, "Wartość spoza enum.");
        }
        if (value.isNull()) return;
        if (value.isObject()) {
            schema.path("required").forEach(key -> {
                if (!schema.path("properties").path(key.asText()).path("readOnly").asBoolean() && !value.has(key.asText()))
                    fail(pointer + "/" + key.asText(), "Wymagane pole.");
            });
            value.fields().forEachRemaining(entry -> {
                var child = schema.path("properties").get(entry.getKey());
                if (child != null) {
                    if (child.path("readOnly").asBoolean()) fail(pointer + "/" + entry.getKey(), "Pole readOnly nie może być wysłane w żądaniu.");
                    validate(child, entry.getValue(), pointer + "/" + entry.getKey());
                }
                else if (schema.has("additionalProperties") && !schema.get("additionalProperties").asBoolean())
                    fail(pointer + "/" + entry.getKey(), "Nieznane pole.");
            });
        }
        if (value.isArray()) value.forEach(item -> validate(schema.get("items"), item, pointer + "/*"));
        if (value.isNumber()) {
            bound(schema, "minimum", value.decimalValue(), true, pointer);
            bound(schema, "maximum", value.decimalValue(), false, pointer);
        }
        if (value.isTextual()) {
            var length = BigDecimal.valueOf(value.asText().codePointCount(0, value.asText().length()));
            bound(schema, "minLength", length, true, pointer); bound(schema, "maxLength", length, false, pointer);
        }
        if (value.isArray()) {
            bound(schema, "minItems", BigDecimal.valueOf(value.size()), true, pointer);
            bound(schema, "maxItems", BigDecimal.valueOf(value.size()), false, pointer);
        }
    }
    /** Projects OpenAPI request schemas to JSON Schema without changing the stored YAML. */
    public static JsonNode toolArguments(JsonNode schema) {
        var result = (ObjectNode) schema.deepCopy();
        result.remove("readOnly"); result.remove("nullable");
        if (schema.path("nullable").asBoolean())
            result.set("type", result.arrayNode().add(schema.path("type").asText()).add("null"));
        if (schema.has("properties")) {
            var properties = result.putObject("properties");
            schema.get("properties").fields().forEachRemaining(entry -> {
                if (!entry.getValue().path("readOnly").asBoolean()) properties.set(entry.getKey(), toolArguments(entry.getValue()));
            });
        }
        if (schema.has("required")) {
            var required = result.putArray("required");
            schema.get("required").forEach(key -> {
                if (!schema.path("properties").path(key.asText()).path("readOnly").asBoolean()) required.add(key);
            });
        }
        if (schema.has("items")) result.set("items", toolArguments(schema.get("items")));
        return result;
    }
    private static void bound(JsonNode schema, String key, BigDecimal value, boolean lower, String pointer) {
        if (schema.has(key) && (lower ? value.compareTo(schema.get(key).decimalValue()) < 0
                : value.compareTo(schema.get(key).decimalValue()) > 0)) fail(pointer, "Naruszone " + key);
    }
    public static void fail(String pointer, String message) { throw new OpenApiException(pointer + ": " + message); }
}
