package pl.mkn.tdw.integrations.openapi.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import pl.mkn.tdw.integrations.httpauth.HttpAuthenticationPort;
import pl.mkn.tdw.integrations.openapi.OpenApiExecutionPort;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiArguments;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiExecution;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiResult;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiFile;
import pl.mkn.tdw.integrations.openapi.service.OpenApiSchema;
import pl.mkn.tdw.integrations.openapi.service.OpenApiTarget;

import pl.mkn.tdw.integrations.support.http.IntegrationRestClientBuilderFactory;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;

@Component
public class OpenApiRestExecutor implements OpenApiExecutionPort {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final HttpAuthenticationPort authentication;

    public OpenApiRestExecutor(IntegrationRestClientBuilderFactory builders, ObjectMapper mapper,
                               HttpAuthenticationPort authentication) {
        this.mapper = mapper;
        this.authentication = authentication;
        var factory = builders.newRequestFactory(Duration.ofSeconds(3), Duration.ofSeconds(8));
        this.client = builders.newBuilder().requestFactory(factory).build();
    }

    @Override
    public OpenApiResult execute(OpenApiExecution execution, OpenApiArguments requestArguments) {
        var operation = execution.operation();
        var arguments = requestArguments.json();
        OpenApiSchema.validate(operation.validationSchema(), arguments, "arguments");
        var base = operation.server().toString().replaceAll("/+$", "");
        var pathTemplate = operation.path();
        var variables = new LinkedHashMap<String, String>();
        // Reserve all path variables before generating any query template variables.
        operation.parameters().stream().filter(parameter -> parameter.in().equals("path"))
                .forEach(parameter -> variables.put(parameter.name(), ""));
        for (var parameter : operation.parameters()) {
            if (!parameter.in().equals("path")) continue;
            var value = arguments.path(parameter.in()).get(parameter.name());
            if (value == null) continue;
            if (parameter.catchAll()) {
                var path = value.asText();
                if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains("%")
                        || path.contains("?") || path.contains("#") || path.matches("(?i)^[a-z][a-z0-9+.-]*:.*") || path.chars().anyMatch(Character::isISOControl))
                    throw new OpenApiException("Catch-all wymaga względnej ścieżki bez URL, początkowego /, %, query/hash i znaków sterujących.");
                var segments = new ArrayList<String>();
                for (var segment : path.split("/", -1)) {
                    if (segment.equals(".") || segment.equals("..")) throw new OpenApiException("Niedozwolony segment ścieżki.");
                    segments.add(queryVariable(variables, segment));
                }
                pathTemplate = pathTemplate.replace("{" + parameter.name() + "}", String.join("/", segments));
                continue;
            }
            if (value.asText().contains("/") || value.asText().contains("\\") || value.asText().contains("%"))
                throw new OpenApiException("Parametr path musi być pojedynczym segmentem bez slash i kodowania URL.");
            if (value.asText().equals(".") || value.asText().equals(".."))
                throw new OpenApiException("Niedozwolony segment ścieżki.");
            variables.put(parameter.name(), value.asText());
        }
        var uri = UriComponentsBuilder.fromUriString(base + pathTemplate);
        var queryNames = new java.util.HashSet<String>();
        for (var parameter : operation.parameters()) {
            if (!parameter.in().equals("query")) continue;
            var value = arguments.path("query").get(parameter.name());
            if (value == null) continue;
            if (parameter.queryObject()) {
                var fields = value.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    validateQueryName(field.getKey(), queryNames);
                    if (!field.getValue().isValueNode() || field.getValue().isNull())
                        throw new OpenApiException("Query object wymaga skalarnych wartości bez null i zagnieżdżeń.");
                    uri.queryParam(queryVariable(variables, field.getKey()), queryVariable(variables, field.getValue().asText()));
                }
                continue;
            }
            validateQueryName(parameter.name(), queryNames);
            if (value.isArray()) {
                if (value.isEmpty()) throw new OpenApiException("Query „" + parameter.name() + "”: pusta tablica nie ma obsługiwanej reprezentacji URL. Podaj elementy albo pomiń opcjonalny parametr.");
                var placeholders = new ArrayList<String>();
                for (var element : value) {
                    if (element.isContainerNode() || element.isNull())
                        throw new OpenApiException("Elementy tablicy query muszą być nie-nullowymi skalarami.");
                    var placeholder = queryVariable(variables, element.asText());
                    if (parameter.explode()) uri.queryParam(parameter.name(), placeholder);
                    else placeholders.add(placeholder);
                }
                if (!parameter.explode()) uri.queryParam(parameter.name(), String.join(",", placeholders));
            } else {
                uri.queryParam(parameter.name(), queryVariable(variables, value.asText()));
            }
        }
        var target = uri.encode().buildAndExpand(variables).toUri();
        OpenApiTarget.uri(target.getScheme() + "://" + target.getRawAuthority());
        if (!target.getAuthority().equals(operation.server().getAuthority()))
            throw new OpenApiException("Zmiana hosta jest zabroniona.");
        try {
            var accepted = operation.responseContentTypes().values().stream().flatMap(List::stream).distinct()
                    .map(MediaType::parseMediaType).toArray(MediaType[]::new);
            var request = client.method(HttpMethod.valueOf(operation.method())).uri(target)
                    .accept(accepted.length == 0 ? new MediaType[]{MediaType.APPLICATION_JSON} : accepted);
            if (arguments.has("body")) {
                if (operation.upload() != null) OpenApiUploadBody.apply(request, operation.upload(), requestArguments, mapper);
                else request.contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsBytes(arguments.get("body")));
            }
            var authorization = this.authentication.authorization(execution.authentication());
            if (authorization != null) request.header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization);
            return request.exchange((sent, response) -> {
                var bytes = response.getBody().readAllBytes();
                JsonNode body;
                OpenApiFile file = null;
                try {
                    var status = response.getStatusCode().value();
                    var contentType = response.getHeaders().getContentType();
                    var mime = contentType == null ? "" : contentType.getType().toLowerCase(java.util.Locale.ROOT)
                            + "/" + contentType.getSubtype().toLowerCase(java.util.Locale.ROOT);
                    var declared = operation.responseContentTypes().getOrDefault(Integer.toString(status),
                            operation.responseContentTypes().getOrDefault("default", List.of()));
                    var textual = mime.startsWith("text/") || mime.equals("application/json") || mime.endsWith("+json");
                    if (status >= 200 && status < 300 && status != 204 && status != 205
                            && declared.contains("application/octet-stream") && !textual) {
                        var detected = OpenApiBinaryFormat.detect(bytes);
                        if (detected == null || (!mime.isEmpty() && !mime.equals("application/octet-stream")
                                && !mime.equals(detected)))
                            return new OpenApiResult("RESPONSE_RECEIVED", status,
                                    mapper.createObjectNode().put("fileDelivery", "FAILED")
                                            .put("fileError", "Nie rozpoznano obsługiwanego formatu pliku lub jest on niezgodny z Content-Type."),
                                    "HTTP wykonano, ale zawartości pliku nie można przekazać do AI.", null);
                        var name = fileName(response.getHeaders().getContentDisposition().getFilename(),
                                OpenApiFile.EXTENSIONS.get(detected));
                        file = new OpenApiFile(name, detected, bytes);
                        body = mapper.createObjectNode().put("name", name).put("contentType", detected).put("size", bytes.length);
                    } else if (status != 204 && status != 205 && OpenApiFile.EXTENSIONS.containsKey(mime)
                            && (declared.contains(mime) || declared.contains("*/*"))) {
                        var name = response.getHeaders().getContentDisposition().getFilename();
                        name = fileName(name, OpenApiFile.EXTENSIONS.get(mime));
                        file = new OpenApiFile(name, mime, bytes);
                        body = mapper.createObjectNode().put("name", name).put("contentType", mime).put("size", bytes.length);
                    } else body = responseBody(operation, status, contentType, bytes);
                } catch (java.io.IOException | IllegalArgumentException ex) {
                    return unknown(operation, "Odpowiedź ma nieobsługiwany typ, niepoprawny JSON lub kodowanie tekstu. Nie ustalono wyniku biznesowego.");
                }
                return new OpenApiResult("RESPONSE_RECEIVED", response.getStatusCode().value(), body,
                        "Odpowiedź HTTP nie potwierdza wszystkich skutków biznesowych.", file);
            });
        } catch (pl.mkn.tdw.integrations.httpauth.contract.HttpAuthException failure) {
            return new OpenApiResult("AUTH_REQUIRED", null, mapper.nullNode(), failure.getMessage());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new OpenApiException("Nie udało się przygotować JSON request body.");
        } catch (org.springframework.web.client.RestClientException ex) {
            return unknown(operation, "Błąd połączenia lub timeout. Nie ponawiaj samoczynnie zapisu; ustal stan przez odczyt.");
        }
    }

    private static String fileName(String name, String extension) {
        if (name == null) return "response." + extension;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isBlank() || name.chars().anyMatch(Character::isISOControl)) return "response." + extension;
        return name;
    }

    private JsonNode responseBody(OpenApiOperation operation, int status, MediaType contentType, byte[] bytes) throws java.io.IOException {
        if (status == 204 || status == 205) return mapper.nullNode();
        var declared = operation.responseContentTypes().getOrDefault(Integer.toString(status),
                operation.responseContentTypes().getOrDefault("default", List.of()));
        if (declared.contains("*/*")) {
            if (bytes.length == 0) return mapper.nullNode();
            if (contentType != null && contentType.getType().equalsIgnoreCase("application")
                    && (contentType.getSubtype().equalsIgnoreCase("json") || contentType.getSubtype().toLowerCase(java.util.Locale.ROOT).endsWith("+json"))) {
                var json = mapper.readTree(bytes);
                if (json == null || json.isMissingNode()) throw new IllegalArgumentException("Brak wartości JSON.");
                return json;
            }
            if (contentType != null && contentType.getType().equalsIgnoreCase("text")) return decodeText(contentType, bytes);
            return mapper.createObjectNode().put("encoding", "base64")
                    .put("contentType", contentType == null ? "application/octet-stream" : contentType.toString())
                    .put("data", java.util.Base64.getEncoder().encodeToString(bytes));
        }
        if (contentType != null && contentType.getType().equalsIgnoreCase("text") && contentType.getSubtype().equalsIgnoreCase("plain")) {
            if (!declared.contains("text/plain")) throw new IllegalArgumentException("Niezadeklarowane text/plain.");
            return decodeText(contentType, bytes);
        }
        if (bytes.length == 0) return mapper.nullNode();
        if (contentType != null && !(contentType.getType().equalsIgnoreCase("application") && contentType.getSubtype().equalsIgnoreCase("json")))
            throw new IllegalArgumentException("Nieobsługiwany Content-Type.");
        var json = mapper.readTree(bytes);
        if (json == null || json.isMissingNode()) throw new IllegalArgumentException("Brak wartości JSON.");
        return json;
    }

    private String queryVariable(Map<String, String> variables, String value) {
        var key = "__query" + variables.size();
        while (variables.containsKey(key)) key = "_" + key;
        variables.put(key, value);
        return "{" + key + "}";
    }

    private JsonNode decodeText(MediaType contentType, byte[] bytes) throws java.nio.charset.CharacterCodingException {
        var charset = contentType.getCharset() == null ? StandardCharsets.UTF_8 : contentType.getCharset();
        return mapper.getNodeFactory().textNode(charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
    }

    private void validateQueryName(String name, java.util.Set<String> names) {
        if (name.isBlank() || name.chars().anyMatch(Character::isISOControl)) throw new OpenApiException("Query wymaga niepustej nazwy bez znaków sterujących.");
        if (!names.add(name)) throw new OpenApiException("Powtórzona nazwa query: " + name);
    }

    private OpenApiResult unknown(OpenApiOperation operation, String message) {
        return new OpenApiResult(operation.mutation() ? "OUTCOME_UNKNOWN" : "REQUEST_FAILED", null, mapper.nullNode(), message);
    }
}
