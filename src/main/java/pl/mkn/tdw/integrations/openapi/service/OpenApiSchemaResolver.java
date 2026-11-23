package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;

/** Local schema refs only. Requests expand inline; responses retain their graph. */
final class OpenApiSchemaResolver {
    private final ObjectMapper mapper;
    private final JsonNode document;
    private final boolean version31;

    OpenApiSchemaResolver(ObjectMapper mapper, JsonNode document) {
        this.mapper = mapper; this.document = document;
        version31 = document.path("openapi").asText().startsWith("3.1.");
    }

    JsonNode request(JsonNode schema, String pointer) {
        var result = expand(schema, pointer, new HashSet<>(), 0, new int[]{30_000});
        OpenApiSchema.check(result, pointer, 0);
        return result;
    }

    void response(JsonNode schema, String pointer) {
        visit(schema, pointer, new HashSet<>(), 0, new int[]{30_000});
    }

    private JsonNode reference(JsonNode schema, String pointer) {
        var ref = schema.path("$ref");
        if (!ref.isTextual() || !ref.asText().startsWith("#/components/schemas/"))
            OpenApiSchema.fail(pointer, "Dozwolone są tylko lokalne schema refs #/components/schemas/...");
        if (!version31 && schema.size() != 1) OpenApiSchema.fail(pointer, "OpenAPI 3.0 nie obsługuje pól obok $ref schematu.");
        var target = document.at(ref.asText().substring(1));
        if (target.isMissingNode()) OpenApiSchema.fail(pointer, "Nieznana referencja: " + ref.asText());
        var aliases = new HashSet<String>(); aliases.add(ref.asText());
        var cursor = target;
        while (cursor.isObject() && cursor.has("$ref")) {
            var next = cursor.path("$ref").asText();
            if (!aliases.add(next) || aliases.size() > 64) OpenApiSchema.fail(pointer, "Cykliczny łańcuch aliasów schema refs.");
            if (!next.startsWith("#/components/schemas/")) OpenApiSchema.fail(pointer, "Dozwolone są tylko lokalne schema refs.");
            cursor = document.at(next.substring(1));
            if (cursor.isMissingNode()) OpenApiSchema.fail(pointer, "Nieznana referencja: " + next);
        }
        return target;
    }

    private JsonNode expand(JsonNode node, String pointer, Set<String> active, int depth, int[] budget) {
        bound(pointer, depth, budget);
        if (node.isObject() && node.has("$ref")) {
            var target = reference(node, pointer); var ref = node.get("$ref").asText();
            if (!active.add(ref)) OpenApiSchema.fail(pointer, "Rekurencyjny request schema nie mieści się w projekcji inline: " + ref);
            var resolved = expand(target, ref, active, depth + 1, budget); active.remove(ref);
            if (node.size() == 1) return resolved;
            var siblings = (ObjectNode) node.deepCopy(); siblings.remove("$ref");
            var result = mapper.createObjectNode();
            result.putArray("allOf").add(resolved).add(expand(siblings, pointer, active, depth + 1, budget));
            if (resolved.path("readOnly").asBoolean()) result.put("readOnly", true);
            for (var annotation : Set.of("readOnly", "description", "title"))
                if (siblings.has(annotation) && !(annotation.equals("readOnly") && result.path("readOnly").asBoolean())) result.set(annotation, siblings.get(annotation));
            return result;
        }
        if (node.isObject()) {
            dialect(node, pointer);
            var result = mapper.createObjectNode();
            node.fields().forEachRemaining(e -> {
                var key = e.getKey(); var value = e.getValue();
                if (key.equals("properties") && value.isObject()) {
                    var properties = result.putObject(key);
                    value.fields().forEachRemaining(p -> properties.set(p.getKey(), expand(p.getValue(), pointer + "/properties/" + p.getKey(), active, depth + 1, budget)));
                } else result.set(key, Set.of("items", "additionalProperties", "allOf", "oneOf", "anyOf").contains(key)
                        ? expand(value, pointer + "/" + key, active, depth + 1, budget) : value.deepCopy());
            });
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode(); node.forEach(child -> result.add(expand(child, pointer, active, depth + 1, budget))); return result;
        }
        return node.deepCopy();
    }

    private void visit(JsonNode node, String pointer, Set<String> visited, int depth, int[] budget) {
        bound(pointer, depth, budget);
        if (node.isObject() && node.has("$ref")) {
            var target = reference(node, pointer);
            if (visited.add(node.get("$ref").asText())) visit(target, node.get("$ref").asText(), visited, 0, budget);
            if (node.size() == 1) return;
            var siblings = (ObjectNode) node.deepCopy(); siblings.remove("$ref"); visit(siblings, pointer, visited, depth + 1, budget); return;
        }
        OpenApiSchema.checkShallow(node, pointer);
        dialect(node, pointer);
        var mapping = node.path("discriminator").path("mapping");
        mapping.fields().forEachRemaining(e -> {
            var ref = e.getValue().asText();
            if (!ref.startsWith("#/components/schemas/") || document.at(ref.substring(1)).isMissingNode())
                OpenApiSchema.fail(pointer + "/discriminator/mapping/" + e.getKey(), "Wymagane istniejące lokalne schema ref.");
            if (visited.add(ref)) visit(document.at(ref.substring(1)), ref, visited, 0, budget);
        });
        node.path("properties").fields().forEachRemaining(e -> visit(e.getValue(), pointer + "/properties/" + e.getKey(), visited, depth + 1, budget));
        for (var key : Set.of("items", "additionalProperties"))
            if (node.has(key) && !node.get(key).isBoolean()) visit(node.get(key), pointer + "/" + key, visited, depth + 1, budget);
        for (var key : Set.of("allOf", "oneOf", "anyOf"))
            for (var child : node.path(key)) visit(child, pointer + "/" + key, visited, depth + 1, budget);
    }

    private void bound(String pointer, int depth, int[] budget) {
        if (--budget[0] < 0 || depth > 64) OpenApiSchema.fail(pointer, "Schema przekracza budżet 30000 węzłów / 64 poziomów.");
    }

    private void dialect(JsonNode node, String pointer) {
        if (!version31 && (node.isBoolean() || node.path("type").isArray() || node.path("type").asText().equals("null") || node.has("const")))
            OpenApiSchema.fail(pointer, "Konstrukcja wymaga OpenAPI 3.1.");
        if (version31 && node.has("nullable")) OpenApiSchema.fail(pointer, "OpenAPI 3.1 używa type z null zamiast nullable.");
        for (var key : Set.of("exclusiveMinimum", "exclusiveMaximum")) {
            if (node.has(key) && (version31 ? !node.get(key).isNumber() : !node.get(key).isBoolean()))
                OpenApiSchema.fail(pointer + "/" + key, "Ograniczenie ma nieprawidłowy typ dla dialektu OpenAPI.");
            if (!version31 && node.path(key).asBoolean() && !node.has(key.equals("exclusiveMinimum") ? "minimum" : "maximum"))
                OpenApiSchema.fail(pointer + "/" + key, "Brak odpowiedniego minimum/maximum.");
        }
        node.path("discriminator").path("mapping").fields().forEachRemaining(e -> {
            var ref = e.getValue().asText();
            if (!ref.startsWith("#/components/schemas/") || document.at(ref.substring(1)).isMissingNode())
                OpenApiSchema.fail(pointer + "/discriminator/mapping/" + e.getKey(), "Wymagane istniejące lokalne schema ref.");
        });
    }
}
