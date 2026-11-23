package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Non-blocking contract visibility, never inferred mappings or execution instructions. */
final class OpenApiSchemaDiagnostics {
    private OpenApiSchemaDiagnostics() {}

    static List<String> warnings(JsonNode document, JsonNode path, JsonNode operation) {
        var flags = new HashSet<String>();
        inspect(document, operation, new HashSet<>(), flags, 0);
        var warnings = new ArrayList<String>();
        var omitted = OpenApiParameters.effective(path, operation).stream().filter(OpenApiParameters::optionalHeader)
                .map(parameter -> parameter.path("name").asText()).toList();
        if (!omitted.isEmpty()) warnings.add("Opcjonalne nagłówki są pomijane i nie mogą być podane przez AI: " + String.join(", ", omitted) + ".");
        if (flags.contains("oneOf")) warnings.add("oneOf wymaga dokładnie jednego dopasowania. Nakładające się warianty są odrzucane przed HTTP.");
        if (flags.contains("discriminator")) warnings.add("Discriminator bez jawnego mapping: importer nie zgaduje powiązań wartości z typami.");
        if (!operation.has("x-tdw-operation-effect")) warnings.add("Skutki operacji niepotwierdzone; także GET jest traktowany jak potencjalny zapis.");
        return List.copyOf(warnings);
    }

    private static void inspect(JsonNode root, JsonNode node, Set<String> visited, Set<String> flags, int depth) {
        if (depth > 100 || visited.size() > 10_000) return;
        if (node.isObject()) {
            if (node.has("oneOf")) flags.add("oneOf");
            if (node.has("discriminator") && !node.get("discriminator").has("mapping")) flags.add("discriminator");
            var ref = node.path("$ref").asText();
            if (ref.startsWith("#/components/") && visited.add(ref)) inspect(root, root.at(ref.substring(1)), visited, flags, 0);
            node.fields().forEachRemaining(e -> {
                if (!Set.of("example", "examples", "default").contains(e.getKey())) inspect(root, e.getValue(), visited, flags, depth + 1);
            });
        } else if (node.isArray()) node.forEach(child -> inspect(root, child, visited, flags, depth + 1));
    }
}
