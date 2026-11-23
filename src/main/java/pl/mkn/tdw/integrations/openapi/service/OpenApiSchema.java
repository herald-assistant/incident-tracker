package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.regex.JDKRegularExpressionFactory;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import java.util.*;

/** OpenAPI request adaptation; JSON Schema owns composition and value validation. */
public final class OpenApiSchema {
    private OpenApiSchema() {}
    private static final Set<String> FIELDS = Set.of("type", "description", "title", "properties", "items",
            "required", "enum", "const", "additionalProperties", "minimum", "maximum", "minLength", "maxLength",
            "minItems", "maxItems", "example", "examples", "default", "readOnly", "writeOnly", "format", "nullable",
            "allOf", "oneOf", "anyOf", "discriminator", "uniqueItems", "deprecated", "exclusiveMinimum", "exclusiveMaximum", "pattern");
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaLoader(loader -> loader.fetchRemoteResources(false).allow(iri -> false))
                    .schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(false)
                            .regularExpressionFactory(JDKRegularExpressionFactory.getInstance()).build()));
    // Contains schemas only, never arguments, authentication or responses.
    private static final Map<JsonNode, Schema> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<JsonNode, Schema> entry) { return size() > 32; }
    });

    public static void check(JsonNode schema, String pointer, int depth) {
        if (depth > 64) fail(pointer, "Schema przekracza 64 poziomy.");
        checkShallow(schema, pointer);
        schema.path("properties").fields().forEachRemaining(e -> check(e.getValue(), pointer + "/properties/" + e.getKey(), depth + 1));
        for (var key : Set.of("items", "additionalProperties"))
            if (schema.has(key) && !schema.get(key).isBoolean()) check(schema.get(key), pointer + "/" + key, depth + 1);
        for (var key : Set.of("allOf", "oneOf", "anyOf"))
            for (var child : schema.path(key)) check(child, pointer + "/" + key, depth + 1);
    }

    static void checkShallow(JsonNode schema, String pointer) {
        if (schema.isBoolean()) return;
        if (!schema.isObject()) fail(pointer, "Schema musi być obiektem lub boolean.");
        schema.fieldNames().forEachRemaining(key -> {
            if (!FIELDS.contains(key) && !key.startsWith("x-")) fail(pointer + "/" + key, "Nieobsługiwane pole schematu.");
        });
        if (schema.has("type")) {
            var types = schema.get("type"); var values = new ArrayList<JsonNode>();
            if (types.isArray()) types.forEach(values::add); else values.add(types);
            if (values.isEmpty() || values.stream().anyMatch(type -> !type.isTextual() || !Set.of("object", "array", "string", "number", "integer", "boolean", "null").contains(type.asText()))
                    || new HashSet<>(values).size() != values.size()) fail(pointer + "/type", "Nieprawidłowy type.");
        }
        for (var key : Set.of("readOnly", "writeOnly", "nullable", "uniqueItems", "deprecated"))
            if (schema.has(key) && !schema.get(key).isBoolean()) fail(pointer + "/" + key, key + " musi być boolean.");
        for (var key : Set.of("format", "description", "title"))
            if (schema.has(key) && !schema.get(key).isTextual()) fail(pointer + "/" + key, key + " musi być string.");
        if (schema.has("pattern")) {
            if (!schema.get("pattern").isTextual()) fail(pointer + "/pattern", "pattern musi być string.");
            try {
                REGISTRY.getSchemaRegistryConfig().getRegularExpressionFactory().getRegularExpression(schema.get("pattern").textValue());
            } catch (RuntimeException ex) {
                fail(pointer + "/pattern", "Nieprawidłowy lub nieobsługiwany regex pattern.");
            }
        }
        if (schema.has("properties") && !schema.get("properties").isObject()) fail(pointer, "properties musi być obiektem.");
        if (schema.has("required") && (!schema.get("required").isArray() || !allTextUnique(schema.get("required")))) fail(pointer, "required musi być tablicą unikalnych nazw.");
        if (schema.has("enum") && (!schema.get("enum").isArray() || schema.get("enum").isEmpty())) fail(pointer, "enum musi być niepustą tablicą.");
        for (var key : Set.of("allOf", "oneOf", "anyOf"))
            if (schema.has(key) && (!schema.get(key).isArray() || schema.get(key).isEmpty())) fail(pointer + "/" + key, "Wymagana niepusta tablica schematów.");
        if (schema.has("additionalProperties") && !schema.get("additionalProperties").isObject() && !schema.get("additionalProperties").isBoolean()) fail(pointer, "additionalProperties musi być schema lub boolean.");
        for (var key : Set.of("minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems"))
            if (schema.has(key) && (!schema.get(key).isNumber() || (!Set.of("minimum", "maximum").contains(key) && (!schema.get(key).isIntegralNumber() || schema.get(key).asLong() < 0))))
                fail(pointer + "/" + key, "Nieprawidłowe ograniczenie.");
        for (var pair : new String[][]{{"minimum", "maximum"}, {"minLength", "maxLength"}, {"minItems", "maxItems"}})
            if (schema.has(pair[0]) && schema.has(pair[1]) && schema.get(pair[0]).decimalValue().compareTo(schema.get(pair[1]).decimalValue()) > 0)
                fail(pointer, "Sprzeczne ograniczenia " + pair[0] + "/" + pair[1]);
        for (var key : Set.of("exclusiveMinimum", "exclusiveMaximum"))
            if (schema.has(key) && !schema.get(key).isNumber() && !schema.get(key).isBoolean()) fail(pointer, "Nieprawidłowe " + key);
        if (schema.has("discriminator")) {
            var discriminator = schema.get("discriminator");
            if (!discriminator.isObject() || !discriminator.path("propertyName").isTextual() || discriminator.path("propertyName").asText().isBlank()) fail(pointer + "/discriminator", "Wymagane propertyName.");
            discriminator.fieldNames().forEachRemaining(key -> {
                if (!Set.of("propertyName", "mapping").contains(key) && !key.startsWith("x-")) fail(pointer + "/discriminator/" + key, "Nieobsługiwane pole.");
            });
            if (discriminator.has("mapping")) {
                if (!discriminator.get("mapping").isObject()) fail(pointer, "mapping musi być obiektem.");
                discriminator.get("mapping").forEach(value -> { if (!value.isTextual()) fail(pointer, "mapping musi zawierać tekstowe referencje."); });
            }
        }
    }

    private static boolean allTextUnique(JsonNode array) {
        var seen = new HashSet<String>();
        for (var value : array) if (!value.isTextual() || !seen.add(value.asText())) return false;
        return true;
    }

    public static void validate(JsonNode schema, JsonNode value, String pointer) {
        if (value == null) fail(pointer, "Wymagany JSON argumentów.");
        var compiled = CACHE.get(schema);
        if (compiled == null) { compiled = REGISTRY.getSchema(project(schema, false, Set.of())); CACHE.put(schema.deepCopy(), compiled); }
        var errors = compiled.validate(value);
        if (!errors.isEmpty()) {
            var details = errors.stream().limit(4).map(error -> error.getInstanceLocation() + " [" + error.getKeyword() + "]").toList();
            fail(pointer, "Argumenty nie spełniają schematu: " + String.join(", ", details)
                    + ". oneOf wymaga dokładnie jednego wariantu; allOf wszystkich ograniczeń. Pola readOnly są zabronione.");
        }
    }

    public static JsonNode toolArguments(JsonNode schema) { return project(schema, true, Set.of()); }

    private static JsonNode project(JsonNode schema, boolean tool, Set<String> inheritedReadOnly) {
        if (schema.isBoolean()) return schema.deepCopy();
        var result = (ObjectNode) schema.deepCopy();
        var readOnly = new HashSet<>(inheritedReadOnly); collectReadOnly(schema, readOnly);
        result.remove(List.of("readOnly", "writeOnly", "nullable", "discriminator"));
        if (!tool) result.remove("example");
        if (tool && schema.has("discriminator")) result.put("description", schema.path("description").asText("") + "\nDiscriminator: " + schema.get("discriminator"));
        var extensions = new ArrayList<String>(); result.fieldNames().forEachRemaining(key -> { if (key.startsWith("x-")) extensions.add(key); }); result.remove(extensions);
        if (schema.path("nullable").asBoolean() && schema.path("type").isTextual()) result.set("type", result.arrayNode().add(schema.path("type").asText()).add("null"));
        for (var pair : new String[][]{{"exclusiveMinimum", "minimum"}, {"exclusiveMaximum", "maximum"}})
            if (schema.path(pair[0]).isBoolean()) {
                result.remove(pair[0]);
                if (schema.path(pair[0]).asBoolean() && schema.has(pair[1])) { result.set(pair[0], schema.get(pair[1])); result.remove(pair[1]); }
            }
        if (schema.has("properties")) {
            var properties = result.putObject("properties");
            schema.get("properties").fields().forEachRemaining(entry -> {
                if (!readOnly.contains(entry.getKey())) properties.set(entry.getKey(), project(entry.getValue(), tool, Set.of()));
                else if (!tool) properties.put(entry.getKey(), false);
            });
        }
        if (schema.has("required")) {
            var required = result.putArray("required"); schema.get("required").forEach(key -> { if (!readOnly.contains(key.asText())) required.add(key); });
        }
        for (var key : Set.of("items", "additionalProperties")) if (schema.has(key)) result.set(key, project(schema.get(key), tool, Set.of()));
        for (var key : Set.of("allOf", "oneOf", "anyOf"))
            if (schema.has(key)) {
                var children = result.putArray(key); schema.get(key).forEach(child -> children.add(project(child, tool, key.equals("allOf") ? readOnly : Set.of())));
            }
        return result;
    }

    private static void collectReadOnly(JsonNode schema, Set<String> names) {
        schema.path("properties").fields().forEachRemaining(entry -> { if (isReadOnly(entry.getValue())) names.add(entry.getKey()); });
        schema.path("allOf").forEach(child -> collectReadOnly(child, names));
    }

    private static boolean isReadOnly(JsonNode schema) {
        if (schema.path("readOnly").asBoolean()) return true;
        for (var part : schema.path("allOf")) if (isReadOnly(part)) return true;
        return false;
    }

    public static void fail(String pointer, String message) { throw new OpenApiException(pointer + ": " + message); }
}
