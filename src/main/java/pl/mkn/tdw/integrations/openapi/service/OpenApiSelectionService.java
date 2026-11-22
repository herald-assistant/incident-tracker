package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.openapi.OpenApiImportPort;
import pl.mkn.tdw.integrations.openapi.OpenApiSelectionPort;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiSelection;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OpenApiSelectionService implements OpenApiSelectionPort {
    private static final Set<String> METHODS = Set.of("get", "post", "put", "delete", "patch", "head", "options", "trace");
    private static final Set<String> EXECUTABLE = Set.of("get", "post", "put", "delete");
    private final ObjectMapper mapper;
    private final OpenApiImportPort importer;

    @Override public OpenApiSelection.Document document(String apiId, String yaml) {
        importer.parse(apiId, yaml);
        return new OpenApiSelection.Document(OpenApiYaml.read(mapper, yaml, 100_000));
    }

    @Override public OpenApiSelection.Preview preview(String apiId, String yaml) {
        return inspect(apiId, source(yaml));
    }

    @Override public OpenApiSelection.Prepared select(String apiId, String yaml, List<OpenApiSelection.OperationKey> selection) {
        var source = source(yaml);
        var preview = inspect(apiId, source);
        if (selection == null || selection.isEmpty() || selection.size() > OpenApiSelection.MAX_SELECTED)
            fail("Wybierz od 1 do " + OpenApiSelection.MAX_SELECTED + " operacji.");
        var candidates = new HashMap<OpenApiSelection.OperationKey, OpenApiSelection.Candidate>();
        preview.operations().forEach(candidate -> candidates.put(candidate.key(), candidate));
        var chosen = new LinkedHashSet<OpenApiSelection.OperationKey>();
        for (var key : selection) {
            var candidate = candidates.get(key);
            if (candidate == null || !chosen.add(key)) fail("Wybór zawiera nieznaną lub powtórzoną operację.");
            if (candidate.unavailableReason() != null) fail(candidate.method() + " " + candidate.path() + ": " + candidate.unavailableReason());
        }
        var result = (ObjectNode) source.deepCopy();
        var paths = mapper.createObjectNode();
        for (var key : chosen) {
            var original = source.path("paths").get(key.path());
            if (!paths.has(key.path())) {
                var path = mapper.createObjectNode();
                original.fields().forEachRemaining(entry -> {
                    if (!METHODS.contains(entry.getKey())) path.set(entry.getKey(), entry.getValue().deepCopy());
                });
                paths.set(key.path(), path);
            }
            ((ObjectNode) paths.get(key.path())).set(key.method().toLowerCase(Locale.ROOT),
                    original.get(key.method().toLowerCase(Locale.ROOT)).deepCopy());
        }
        result.set("paths", paths);
        var components = reachableComponents(source, paths);
        var securityNames = OpenApiSecurity.usedSchemes(source, paths);
        if (!securityNames.isEmpty()) {
            var schemes = components.putObject("securitySchemes");
            for (var name : securityNames) {
                var scheme = source.path("components").path("securitySchemes").get(name);
                if (scheme == null) fail("Nieznany securityScheme: " + name);
                schemes.set(name, scheme.deepCopy());
            }
        }
        if (components.isEmpty()) result.remove("components"); else result.set("components", components);
        var usedTags = new HashSet<String>();
        chosen.forEach(key -> usedTags.addAll(candidates.get(key).tags()));
        if (source.path("tags").isArray()) {
            var tags = mapper.createArrayNode();
            source.get("tags").forEach(tag -> { if (usedTags.contains(tag.path("name").asText())) tags.add(tag); });
            if (tags.isEmpty()) result.remove("tags"); else result.set("tags", tags);
        }
        var prepared = OpenApiYaml.write(mapper, result);
        if (prepared.length() > 100_000) fail("Wybrany zestaw wraz ze schematami przekracza 100000 znaków. Zmniejsz wybór.");
        var operations = importer.parse(apiId, prepared);
        return new OpenApiSelection.Prepared(prepared, operations.size());
    }

    private JsonNode source(String yaml) {
        var source = OpenApiYaml.read(mapper, yaml, OpenApiSelection.MAX_SOURCE_CHARACTERS);
        if (!source.isObject() || !source.path("openapi").asText().matches("3\\.0\\.[0-9]+"))
            fail("Wspierane OpenAPI 3.0.x.");
        if (!source.path("paths").isObject()) fail("Wymagany obiekt paths.");
        return source;
    }

    private OpenApiSelection.Preview inspect(String apiId, JsonNode source) {
        if (apiId == null || !apiId.matches("[a-z][a-z0-9_]{0,23}")) fail("Nieprawidłowe ID API.");
        var operations = new ArrayList<OpenApiSelection.Candidate>();
        var paths = source.path("paths").fields();
        while (paths.hasNext()) {
            var path = paths.next();
            if (!path.getValue().isObject()) fail(path.getKey() + ": wymagany obiekt ścieżki.");
            var entries = path.getValue().fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (!METHODS.contains(entry.getKey())) continue;
                var operation = entry.getValue();
                var id = operation.path("operationId").asText("");
                String unavailable = null;
                if (!EXECUTABLE.contains(entry.getKey())) unavailable = "Nieobsługiwana metoda HTTP.";
                else if (!operation.isObject()) unavailable = "Nieprawidłowa definicja operacji.";
                else if (!id.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) unavailable = "Wymagany poprawny operationId (litery, cyfry, podkreślenie).";
                else if (("oa_" + apiId + "_" + id).length() > 64) unavailable = "Nazwa narzędzia przekracza 64 znaki; skróć ID API lub operationId.";
                var tags = new LinkedHashSet<String>();
                if (operation.path("tags").isArray()) operation.get("tags").forEach(tag -> { if (tag.isTextual()) tags.add(tag.asText()); });
                operations.add(new OpenApiSelection.Candidate(entry.getKey().toUpperCase(Locale.ROOT), path.getKey(), id,
                        operation.path("summary").asText(operation.path("description").asText("")), List.copyOf(tags), unavailable));
                if (operations.size() > OpenApiSelection.MAX_CANDIDATES) fail("Maksymalnie 5000 operacji w pliku źródłowym.");
            }
        }
        if (operations.isEmpty()) fail("W pliku nie znaleziono operacji HTTP.");
        return new OpenApiSelection.Preview(source.path("info").path("title").asText(""), operations, OpenApiSelection.MAX_SELECTED, source);
    }

    private ObjectNode reachableComponents(JsonNode source, JsonNode paths) {
        var result = mapper.createObjectNode();
        var pending = new ArrayDeque<String>();
        references(paths, pending);
        var visited = new HashSet<String>();
        while (!pending.isEmpty()) {
            var ref = pending.removeFirst();
            if (!visited.add(ref)) continue;
            if (visited.size() > 10_000) fail("Zbyt wiele zależnych referencji.");
            var segments = ref.split("/", -1);
            if (!ref.startsWith("#/components/") || segments.length < 4) fail("Wybór zawiera nieobsługiwaną referencję: " + ref);
            if (source.at(ref.substring(1)).isMissingNode()) fail("Nieznana referencja: " + ref);
            var section = pointerSegment(segments[2]); var name = pointerSegment(segments[3]);
            if (result.path(section).has(name)) continue;
            var component = source.path("components").path(section).get(name);
            if (component == null) fail("Nieznana referencja: " + ref);
            if (!result.has(section)) result.putObject(section);
            ((ObjectNode) result.get(section)).set(name, component.deepCopy());
            references(component, pending);
        }
        return result;
    }

    private void references(JsonNode node, Deque<String> pending) {
        if (node.isObject() && node.has("$ref")) pending.add(node.get("$ref").asText());
        if (node.isContainerNode()) node.forEach(child -> references(child, pending));
    }
    private String pointerSegment(String value) { return value.replace("~1", "/").replace("~0", "~"); }
    private void fail(String message) { throw new OpenApiException(message); }
}
