package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.openapi.OpenApiImportPort;
import pl.mkn.tdw.integrations.openapi.contract.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OpenApiImporter implements OpenApiImportPort {
    private final ObjectMapper mapper;

    @Override
    public List<OpenApiOperation> parse(String id, String yaml) {
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,23}") || yaml == null || yaml.length() > 100_000)
            throw new OpenApiException("Nieprawidłowe ID lub rozmiar YAML (maks. 100000 znaków).");
        var root = OpenApiYaml.read(mapper, yaml, 100_000);
        fields(root, "", "openapi", "info", "servers", "paths", "components", "tags", "externalDocs", "security");
        if (!root.path("openapi").asText().matches("3\\.0\\.[0-9]+")) error("/openapi", "Wspierane OpenAPI 3.0.x.");
        OpenApiSecurity.validate(root);
        fields(root.path("components"), "/components", "schemas", "securitySchemes");
        var servers = root.path("servers");
        if (!servers.isArray() || servers.size() != 1) error("/servers", "Wymagany dokładnie jeden server.");
        fields(servers.get(0), "/servers/0", "url", "description");
        var server = OpenApiTarget.uri(servers.get(0).path("url").asText());
        var paths = root.path("paths");
        if (!paths.isObject()) error("/paths", "Wymagany obiekt paths.");
        var result = new ArrayList<OpenApiOperation>();
        var names = new HashSet<String>();
        var entries = paths.fields();
        while (entries.hasNext()) {
            var entry = entries.next(); var path = entry.getKey(); var item = entry.getValue();
            if (!path.startsWith("/") || path.contains("?") || path.contains("#") || path.contains("..") || path.contains("%"))
                error(path, "Nieprawidłowa ścieżka.");
            fields(item, path, "get", "post", "put", "delete", "parameters", "summary", "description");
            for (var method : List.of("get", "post", "put", "delete")) {
                if (!item.has(method)) continue;
                var op = item.get(method);
                fields(op, path + "/" + method, "operationId", "summary", "description", "parameters", "requestBody",
                        "responses", "tags", "deprecated", "externalDocs", "security");
                var operationId = op.path("operationId").asText();
                if (("oa_" + id + "_" + operationId).length() > 64) error(path, "Nazwa narzędzia (oa_ID_operationId) przekracza 64 znaki.");
                if (!operationId.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || !names.add(operationId))
                    error(path, "Wymagany unikalny operationId (litery, cyfry, podkreślenie, do 64 znaków).");
                var input = objectSchema(); var required = input.withArray("required");
                var parameters = new ArrayList<OpenApiOperation.Parameter>();
                var allParams = new LinkedHashMap<String, JsonNode>();
                for (var owner : List.of(item, op)) {
                    if (owner.has("parameters") && !owner.get("parameters").isArray()) error(path, "parameters musi być tablicą.");
                    var localNames = new HashSet<String>();
                    for (var param : owner.path("parameters")) {
                        fields(param, path, "name", "in", "description", "required", "schema", "style", "explode", "deprecated", "example");
                        String key = param.path("in").asText() + ":" + param.path("name").asText();
                        if (!localNames.add(key)) error(path, "Powtórzony parametr.");
                        allParams.put(key, param);
                    }
                }
                for (var param : allParams.values()) {
                    var in = param.path("in").asText(); var name = param.path("name").asText();
                    if (!Set.of("path", "query").contains(in) || !name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) error(path, "Tylko nazwane parametry path/query.");
                    if (param.has("style") && !param.get("style").asText().equals(in.equals("path") ? "simple" : "form")) error(path, "Nieobsługiwany style.");
                    if (param.has("explode") && !param.get("explode").isBoolean()) error(path, "explode musi być boolean.");
                    if (param.has("required") && !param.get("required").isBoolean()) error(path, "required musi być boolean.");
                    var schema = resolve(root, param.path("schema"), new HashSet<>(), 0);
                    OpenApiSchema.check(schema, path + "/" + name, 0);
                    // OpenAPI readOnly applies to schema properties, not a whole parameter.
                    ((ObjectNode) schema).remove("readOnly");
                    if (Set.of("object", "array").contains(schema.path("type").asText())) error(path, "Parametry muszą być skalarne.");
                    if (in.equals("path") && !param.path("required").asBoolean()) error(path, "Path parameter musi być required.");
                    if (!input.withObject("properties").has(in)) input.withObject("properties").set(in, objectSchema());
                    var group = (ObjectNode) input.path("properties").get(in);
                    group.withObject("properties").set(name, schema);
                    if (param.path("required").asBoolean()) {
                        group.withArray("required").add(name);
                        if (!required.toString().contains("\"" + in + "\"")) required.add(in);
                    }
                    parameters.add(new OpenApiOperation.Parameter(name, in));
                }
                var tokens = new HashSet<String>();
                var matcher = java.util.regex.Pattern.compile("\\{([^{}]+)}").matcher(path);
                while (matcher.find()) tokens.add(matcher.group(1));
                var declared = new HashSet<String>();
                parameters.stream().filter(p -> p.in().equals("path")).forEach(p -> declared.add(p.name()));
                if (!tokens.equals(declared) || path.replaceAll("\\{[^{}]+}", "").matches(".*[{}].*")) error(path, "Niezgodne parametry ścieżki.");
                if (op.has("requestBody")) {
                    if (method.equals("get") || method.equals("delete")) error(path, "Body tylko dla POST/PUT.");
                    var body = op.get("requestBody"); fields(body, path, "description", "required", "content");
                    if (body.has("required") && !body.get("required").isBoolean()) error(path, "required musi być boolean.");
                    var schema = jsonSchema(root, body.path("content"), path);
                    // A request body is not itself a schema property; retain flags on its children.
                    ((ObjectNode) schema).remove("readOnly");
                    input.withObject("properties").set("body", schema);
                    if (body.path("required").asBoolean()) required.add("body");
                }
                var responses = op.path("responses");
                if (!responses.isObject() || responses.isEmpty()) error(path, "Wymagane responses.");
                var responseEntries = responses.fields();
                while (responseEntries.hasNext()) {
                    var response = responseEntries.next();
                    if (!response.getKey().matches("[1-5][0-9]{2}|default")) error(path, "Nieobsługiwany kod responses.");
                    fields(response.getValue(), path + "/responses", "description", "content");
                    if (response.getValue().has("content")) jsonSchema(root, response.getValue().get("content"), path);
                }
                result.add(new OpenApiOperation("oa_" + id + "_" + operationId,
                        op.path("summary").asText(operationId) + "\n" + op.path("description").asText(""),
                        method.toUpperCase(Locale.ROOT), server, path, parameters, OpenApiSchema.toolArguments(input), input));
                if (result.size() > 60) error(path, "Maksymalnie 60 operacji w specyfikacji.");
            }
        }
        return List.copyOf(result);
    }

    private JsonNode jsonSchema(JsonNode root, JsonNode content, String path) {
        fields(content, path + "/content", "application/json");
        if (!content.has("application/json")) error(path, "Wymagane application/json.");
        var media = content.get("application/json"); fields(media, path, "schema", "example");
        var schema = resolve(root, media.path("schema"), new HashSet<>(), 0);
        OpenApiSchema.check(schema, path, 0); return schema;
    }
    private JsonNode resolve(JsonNode root, JsonNode node, Set<String> visiting, int depth) {
        return resolve(root, node, visiting, depth, new int[]{10_000});
    }
    private JsonNode resolve(JsonNode root, JsonNode node, Set<String> visiting, int depth, int[] budget) {
        if (--budget[0] < 0) error("/$ref", "Rozwinięty schemat przekracza 10000 węzłów.");
        if (depth > 20) { error("/$ref", "Zbyt głęboki schemat."); }
        if (node.isObject() && node.has("$ref")) {
            var ref = node.path("$ref").asText();
            if (node.size() != 1 || !ref.startsWith("#/components/schemas/") || !visiting.add(ref)) error("/$ref", "Tylko lokalne, acykliczne schema refs bez dodatkowych pól.");
            var target = root.at(ref.substring(1));
            if (target.isMissingNode()) error(ref, "Nieznany ref.");
            var result = resolve(root, target, visiting, depth + 1, budget); visiting.remove(ref); return result;
        }
        if (node.isObject()) {
            var output = mapper.createObjectNode();
            node.fields().forEachRemaining(e -> output.set(e.getKey(), resolve(root, e.getValue(), visiting, depth + 1, budget)));
            return output;
        }
        if (node.isArray()) {
            var output = mapper.createArrayNode(); for (var child : node) output.add(resolve(root, child, visiting, depth + 1, budget)); return output;
        }
        return node.deepCopy();
    }
    private ObjectNode objectSchema() {
        var result = mapper.createObjectNode(); result.put("type", "object");
        result.putObject("properties"); result.putArray("required"); result.put("additionalProperties", false); return result;
    }

    private static void fields(JsonNode node, String pointer, String... allowed) {
        if (node.isMissingNode()) return;
        if (!node.isObject()) error(pointer, "Wymagany obiekt.");
        var supported = Set.of(allowed);
        node.fieldNames().forEachRemaining(key -> { if (!supported.contains(key)) error(pointer + "/" + key, "Nieobsługiwane pole."); });
    }
    private static void error(String pointer, String message) { OpenApiSchema.fail(pointer, message); }
}
