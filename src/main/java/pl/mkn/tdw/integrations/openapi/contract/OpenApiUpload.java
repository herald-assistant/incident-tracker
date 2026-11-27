package pl.mkn.tdw.integrations.openapi.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Binary positions and their wire encoding, derived exclusively from the selected operation. */
public record OpenApiUpload(List<String> rawMediaTypes, Map<String, Part> parts) {
    public OpenApiUpload {
        rawMediaTypes = List.copyOf(rawMediaTypes);
        parts = Map.copyOf(parts);
    }

    public enum Kind { FILE, FILE_ARRAY, TEXT, JSON }
    public record Part(Kind kind, String mediaType) {}

    public OpenApiArguments bind(JsonNode arguments, Map<String, OpenApiFile> available) {
        if (arguments == null || !arguments.isObject()) throw new OpenApiException("Argumenty muszą być obiektem JSON.");
        var selected = new LinkedHashMap<String, OpenApiFile>();
        var body = arguments.path("body");
        if (!body.isMissingNode()) {
            if (!rawMediaTypes.isEmpty()) resolve(body, available, selected);
            else parts.forEach((name, part) -> {
                var value = body.get(name);
                if (value == null) return;
                if (part.kind() == Kind.FILE) resolve(value, available, selected);
                if (part.kind() == Kind.FILE_ARRAY) value.forEach(item -> resolve(item, available, selected));
            });
        }
        return new OpenApiArguments(arguments, selected);
    }

    private static void resolve(JsonNode reference, Map<String, OpenApiFile> available, Map<String, OpenApiFile> selected) {
        var id = reference.path("attachmentId").asText("");
        var file = available.get(id);
        if (file == null) throw new OpenApiException("Wskazany plik nie jest dostępny w tym wywołaniu.");
        selected.put(id, file);
    }
}
