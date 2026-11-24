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
        var uri = UriComponentsBuilder.fromUriString(base + operation.path());
        var variables = new LinkedHashMap<String, String>();
        // Reserve all path variables before generating any query template variables.
        for (var parameter : operation.parameters()) {
            if (!parameter.in().equals("path")) continue;
            var value = arguments.path(parameter.in()).get(parameter.name());
            if (value == null) continue;
            if (value.asText().contains("/") || value.asText().contains("\\") || value.asText().contains("%"))
                throw new OpenApiException("Parametr path musi być pojedynczym segmentem bez slash i kodowania URL.");
            if (value.asText().equals(".") || value.asText().equals(".."))
                throw new OpenApiException("Niedozwolony segment ścieżki.");
            variables.put(parameter.name(), value.asText());
        }
        for (var parameter : operation.parameters()) {
            if (!parameter.in().equals("query")) continue;
            var value = arguments.path("query").get(parameter.name());
            if (value == null) continue;
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
        final String authorization;
        try { authorization = this.authentication.authorization(execution.authentication()); }
        catch (pl.mkn.tdw.integrations.httpauth.contract.HttpAuthException failure) {
            return new OpenApiResult("AUTH_REQUIRED", null, mapper.nullNode(), failure.getMessage());
        }
        try {
            var accepted = operation.responseContentTypes().values().stream().flatMap(List::stream).distinct()
                    .map(MediaType::parseMediaType).toArray(MediaType[]::new);
            var request = client.method(HttpMethod.valueOf(operation.method())).uri(target)
                    .accept(accepted.length == 0 ? new MediaType[]{MediaType.APPLICATION_JSON} : accepted);
            if (authorization != null) request.header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization);
            if (arguments.has("body")) request.contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsBytes(arguments.get("body")));
            return request.exchange((sent, response) -> {
                var bytes = response.getBody().readAllBytes();
                JsonNode body;
                try {
                    body = responseBody(operation, response.getStatusCode().value(), response.getHeaders().getContentType(), bytes);
                } catch (java.io.IOException | IllegalArgumentException ex) {
                    return unknown(operation, "Odpowiedź ma nieobsługiwany typ, niepoprawny JSON lub kodowanie tekstu. Nie ustalono wyniku biznesowego.");
                }
                return new OpenApiResult("RESPONSE_RECEIVED", response.getStatusCode().value(), body,
                        "Odpowiedź HTTP nie potwierdza wszystkich skutków biznesowych.");
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new OpenApiException("Nie udało się przygotować JSON request body.");
        } catch (org.springframework.web.client.RestClientException ex) {
            return unknown(operation, "Błąd połączenia lub timeout. Nie ponawiaj samoczynnie zapisu; ustal stan przez odczyt.");
        }
    }

    private JsonNode responseBody(OpenApiOperation operation, int status, MediaType contentType, byte[] bytes) throws java.io.IOException {
        if (status == 204 || status == 205) return mapper.nullNode();
        if (contentType != null && contentType.getType().equalsIgnoreCase("text") && contentType.getSubtype().equalsIgnoreCase("plain")) {
            var declared = operation.responseContentTypes().getOrDefault(Integer.toString(status),
                    operation.responseContentTypes().getOrDefault("default", List.of()));
            if (!declared.contains("text/plain")) throw new IllegalArgumentException("Niezadeklarowane text/plain.");
            var charset = contentType.getCharset() == null ? StandardCharsets.UTF_8 : contentType.getCharset();
            var text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return mapper.getNodeFactory().textNode(text);
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

    private OpenApiResult unknown(OpenApiOperation operation, String message) {
        return new OpenApiResult(operation.mutation() ? "OUTCOME_UNKNOWN" : "REQUEST_FAILED", null, mapper.nullNode(), message);
    }
}
