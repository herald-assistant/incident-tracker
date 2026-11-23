package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Effective parameters shared by execution preparation and non-blocking diagnostics. */
final class OpenApiParameters {
    private OpenApiParameters() {}

    static String key(JsonNode parameter) {
        var location = parameter.path("in").asText();
        var name = parameter.path("name").asText();
        return location + ":" + (location.equals("header") ? name.toLowerCase(Locale.ROOT) : name);
    }

    static List<JsonNode> effective(JsonNode path, JsonNode operation) {
        var result = new LinkedHashMap<String, JsonNode>();
        for (var owner : List.of(path, operation)) {
            var parameters = owner.path("parameters");
            if (parameters.isArray()) parameters.forEach(parameter -> {
                if (parameter.isObject()) result.put(key(parameter), parameter);
            });
        }
        return List.copyOf(result.values());
    }

    static boolean optionalHeader(JsonNode parameter) {
        return parameter.path("in").asText().equals("header") && (!parameter.has("required")
                || parameter.get("required").isBoolean() && !parameter.get("required").asBoolean());
    }
}
