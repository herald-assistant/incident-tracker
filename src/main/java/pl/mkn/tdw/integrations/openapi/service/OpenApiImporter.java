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
    private static final Set<String> SCALAR_TYPES = Set.of("string", "integer", "number", "boolean");
    private final ObjectMapper mapper;

    @Override
    public List<OpenApiOperation> parse(String id, String yaml) {
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,23}") || yaml == null || yaml.length() > OpenApiSelection.MAX_DOCUMENT_CHARACTERS)
            throw new OpenApiException("Nieprawidłowe ID lub rozmiar YAML (maks. 1000000 znaków).");
        var root = OpenApiYaml.read(mapper, yaml, OpenApiSelection.MAX_DOCUMENT_CHARACTERS);
        fields(root, "", "openapi", "info", "servers", "paths", "components", "tags", "externalDocs", "security");
        if (!root.path("openapi").asText().matches("3\\.[01]\\.[0-9]+")) error("/openapi", "Wspierane OpenAPI 3.0.x i 3.1.x.");
        OpenApiSecurity.validate(root);
        fields(root.path("components"), "/components", "schemas", "securitySchemes");
        var servers = root.path("servers");
        if (!servers.isArray() || servers.size() != 1) error("/servers", "Wymagany dokładnie jeden server.");
        fields(servers.get(0), "/servers/0", "url", "description");
        var server = OpenApiTarget.uri(servers.get(0).path("url").asText());
        var paths = root.path("paths");
        if (!paths.isObject()) error("/paths", "Wymagany obiekt paths.");
        var resolver = new OpenApiSchemaResolver(mapper, root);
        var result = new ArrayList<OpenApiOperation>();
        var names = new HashSet<String>();
        var toolNames = new HashSet<String>();
        var entries = paths.fields();
        while (entries.hasNext()) {
            var entry = entries.next(); var path = entry.getKey(); var item = entry.getValue();
            if (!path.startsWith("/") || path.contains("?") || path.contains("#") || path.contains("..") || path.contains("%"))
                error(path, "Nieprawidłowa ścieżka.");
            fields(item, path, "get", "post", "put", "delete", "patch", "parameters", "summary", "description");
            for (var method : List.of("get", "post", "put", "delete", "patch")) {
                if (!item.has(method)) continue;
                var op = item.get(method);
                fields(op, path + "/" + method, "operationId", "summary", "description", "parameters", "requestBody",
                        "responses", "tags", "deprecated", "externalDocs", "security");
                var operationId = op.path("operationId").asText();
                if (!OpenApiToolNames.validOperationId(operationId) || !names.add(operationId))
                    error(path, "Wymagany unikalny operationId (pierwszy znak: litera; dalej litery, cyfry, podkreślenie).");
                var toolName = OpenApiToolNames.name(id, operationId);
                if (!toolNames.add(toolName)) error(path, "Kolizja wygenerowanych nazw narzędzi. Zmień ID API lub operationId.");
                var input = objectSchema(); var required = input.withArray("required");
                var parameters = new ArrayList<OpenApiOperation.Parameter>();
                for (var owner : List.of(item, op)) {
                    if (owner.has("parameters") && !owner.get("parameters").isArray()) error(path, "parameters musi być tablicą.");
                    var localNames = new HashSet<String>();
                    for (var param : owner.path("parameters")) {
                        if (!param.isObject()) error(path, "Parametr musi być obiektem.");
                        if (!param.path("in").asText().equals("header"))
                            fields(param, path, "name", "in", "description", "required", "schema", "style", "explode", "deprecated", "example");
                        String key = OpenApiParameters.key(param);
                        if (!localNames.add(key)) error(path, "Powtórzony parametr.");
                    }
                }
                for (var param : OpenApiParameters.effective(item, op)) {
                    var in = param.path("in").asText(); var name = param.path("name").asText();
                    if (param.has("required") && !param.get("required").isBoolean()) error(path, "required musi być boolean.");
                    if (param.has("x-tdw-catch-all") && !param.get("x-tdw-catch-all").isBoolean())
                        error(path, "x-tdw-catch-all musi być boolean.");
                    var catchAll = param.path("x-tdw-catch-all").asBoolean();
                    if (catchAll && !in.equals("path")) error(path, "x-tdw-catch-all jest dozwolone tylko na parametrze path.");
                    if (in.equals("header")) {
                        if (!param.path("name").isTextual() || name.isBlank()) error(path, "Nagłówek wymaga nazwy.");
                        if (!OpenApiParameters.optionalHeader(param)) error(path, "Wymagany nagłówek „" + name + "” nie jest obsługiwany. Narzędzie nie przesyła parametrów header.");
                        continue;
                    }
                    if (!Set.of("path", "query").contains(in) || !name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) error(path, "Tylko nazwane parametry path/query.");
                    if (param.has("style") && !param.get("style").asText().equals(in.equals("path") ? "simple" : "form")) error(path, "Nieobsługiwany style.");
                    if (param.has("explode") && !param.get("explode").isBoolean()) error(path, "explode musi być boolean.");
                    var schema = resolver.request(param.path("schema"), path + "/parameters/" + name);
                    OpenApiSchema.check(schema, path + "/" + name, 0);
                    // OpenAPI readOnly applies to schema properties, not a whole parameter.
                    if (schema instanceof ObjectNode object) object.remove("readOnly");
                    var queryObject = in.equals("query") && schema.path("type").asText().equals("object");
                    if (catchAll) {
                        var token = "{" + name + "}";
                        if (!schema.path("type").asText().equals("string") || !path.endsWith("/" + token)
                                || path.indexOf(token) != path.lastIndexOf(token))
                            error(path, "Catch-all wymaga string i ostatniego pełnego segmentu /{parametr}.");
                        ((ObjectNode) schema).put("description", schema.path("description").asText("")
                                + "\nŚcieżka względna z wieloma segmentami, bez początkowego /, URL, %, query/hash i segmentów . lub ... Host i base path pochodzą z kontraktu.");
                    }
                    if (queryObject) {
                        if (!param.path("explode").asBoolean(true) || schema.path("nullable").asBoolean())
                            error(path, "Query object wymaga form, explode: true i nie-nullowego obiektu.");
                        ((ObjectNode) schema).put("description", schema.path("description").asText("")
                                + "\nQuery: płaska mapa nazw i wartości string/number/boolean. Klucze są parametrami URL; bez zagnieżdżeń, tablic i null.");
                    } else if (!SCALAR_TYPES.contains(schema.path("type").asText())) {
                        if (!in.equals("query") || !schema.path("type").asText().equals("array")
                                || schema.path("nullable").asBoolean()
                                || !SCALAR_TYPES.contains(schema.path("items").path("type").asText())
                                || schema.path("items").path("nullable").asBoolean())
                            error(path + "/parameters/" + name, "Wspierane scalar path/query oraz query array ze skalarnymi, nie-nullowymi items; bez obiektów i tablic zagnieżdżonych.");
                        ((ObjectNode) schema).put("description", schema.path("description").asText("")
                                + "\nQuery: podaj niepustą tablicę. Pomiń opcjonalny parametr, jeżeli nie jest potrzebny.");
                    }
                    if (in.equals("path") && !param.path("required").asBoolean()) error(path, "Path parameter musi być required.");
                    if (!input.withObject("properties").has(in)) input.withObject("properties").set(in, objectSchema());
                    var group = (ObjectNode) input.path("properties").get(in);
                    group.withObject("properties").set(name, schema);
                    if (param.path("required").asBoolean()) {
                        group.withArray("required").add(name);
                        if (!required.toString().contains("\"" + in + "\"")) required.add(in);
                    }
                    parameters.add(new OpenApiOperation.Parameter(name, in, param.path("explode").asBoolean(in.equals("query")), catchAll, queryObject));
                }
                var tokens = new HashSet<String>();
                var matcher = java.util.regex.Pattern.compile("\\{([^{}]+)}").matcher(path);
                while (matcher.find()) tokens.add(matcher.group(1));
                var declared = new HashSet<String>();
                parameters.stream().filter(p -> p.in().equals("path")).forEach(p -> declared.add(p.name()));
                if (!tokens.equals(declared) || path.replaceAll("\\{[^{}]+}", "").matches(".*[{}].*")) error(path, "Niezgodne parametry ścieżki.");
                if (op.has("requestBody")) {
                    if (method.equals("get") || method.equals("delete") && !root.path("openapi").asText().startsWith("3.1."))
                        error(path, "Body dla POST/PUT/PATCH oraz DELETE w OpenAPI 3.1; GET bez body.");
                    var body = op.get("requestBody"); fields(body, path, "description", "required", "content");
                    if (body.has("required") && !body.get("required").isBoolean()) error(path, "required musi być boolean.");
                    var schema = requestSchema(root, body.path("content"), path + "/requestBody");
                    // A request body is not itself a schema property; retain flags on its children.
                    if (schema instanceof ObjectNode object) object.remove("readOnly");
                    input.withObject("properties").set("body", schema);
                    if (body.path("required").asBoolean()) required.add("body");
                }
                var responses = op.path("responses");
                if (!responses.isObject() || responses.isEmpty()) error(path, "Wymagane responses.");
                var responseEntries = responses.fields();
                var responseContentTypes = new LinkedHashMap<String, List<String>>();
                while (responseEntries.hasNext()) {
                    var response = responseEntries.next();
                    if (!response.getKey().matches("[1-5][0-9]{2}|default")) error(path, "Nieobsługiwany kod responses.");
                    fields(response.getValue(), path + "/responses", "description", "content");
                    responseContentTypes.put(response.getKey(), response.getValue().has("content")
                            ? responseContent(root, response.getValue().get("content"), path + "/responses/" + response.getKey()) : List.of());
                }
                var effect = op.path("x-tdw-operation-effect").asText("UNKNOWN");
                if (!Set.of("READ_ONLY", "MUTATION", "UNKNOWN").contains(effect)) error(path, "Nieprawidłowe x-tdw-operation-effect.");
                var toolSchema = OpenApiSchema.toolArguments(input);
                if (toolSchema.toString().length() > OpenApiSelection.MAX_TOOL_SCHEMA_CHARACTERS) error(path, "Schema argumentów przekracza 250000 znaków.");
                result.add(new OpenApiOperation(toolName,
                        op.path("summary").asText(operationId) + "\n" + op.path("description").asText("")
                                + "\noperationId: " + operationId
                                + "\nSkutki operacji: " + effect + ". " + String.join(" ", OpenApiSchemaDiagnostics.warnings(root, item, op)),
                        method.toUpperCase(Locale.ROOT), server, path, parameters, toolSchema, input, effect, responseContentTypes));
                if (result.size() > 60) error(path, "Maksymalnie 60 operacji w specyfikacji.");
            }
        }
        return List.copyOf(result);
    }

    private JsonNode requestSchema(JsonNode root, JsonNode content, String path) {
        fields(content, path + "/content", "application/json");
        if (!content.has("application/json")) error(path, "Wymagane application/json.");
        var media = content.get("application/json"); fields(media, path, "schema", "example");
        var resolver = new OpenApiSchemaResolver(mapper, root);
        return resolver.request(media.path("schema"), path);
    }

    private List<String> responseContent(JsonNode root, JsonNode content, String path) {
        fields(content, path + "/content", "application/json", "text/plain", "*/*");
        if (content.isEmpty()) error(path, "Wymagane application/json, text/plain lub */*.");
        var types = new ArrayList<String>();
        var entries = content.fields();
        var resolver = new OpenApiSchemaResolver(mapper, root);
        while (entries.hasNext()) {
            var entry = entries.next();
            if (entry.getKey().startsWith("x-")) continue;
            var media = entry.getValue(); fields(media, path, "schema", "example");
            if (entry.getKey().equals("text/plain")) {
                var schema = resolver.request(media.path("schema"), path);
                if (!schema.path("type").asText().equals("string")) error(path, "text/plain wymaga schema type: string.");
            } else if (!entry.getKey().equals("*/*") || media.has("schema")) resolver.response(media.path("schema"), path);
            types.add(entry.getKey());
        }
        if (types.isEmpty()) error(path, "Wymagane application/json, text/plain lub */*.");
        return List.copyOf(types);
    }
    private ObjectNode objectSchema() {
        var result = mapper.createObjectNode(); result.put("type", "object");
        result.putObject("properties"); result.putArray("required"); result.put("additionalProperties", false); return result;
    }

    private static void fields(JsonNode node, String pointer, String... allowed) {
        if (node.isMissingNode()) return;
        if (!node.isObject()) error(pointer, "Wymagany obiekt.");
        var supported = Set.of(allowed);
        node.fieldNames().forEachRemaining(key -> { if (!supported.contains(key) && !key.startsWith("x-")) error(pointer + "/" + key, "Nieobsługiwane pole."); });
    }
    private static void error(String pointer, String message) { OpenApiSchema.fail(pointer, message); }
}
