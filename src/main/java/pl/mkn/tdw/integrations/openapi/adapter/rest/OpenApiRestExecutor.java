package pl.mkn.tdw.integrations.openapi.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import pl.mkn.tdw.integrations.openapi.OpenApiExecutionPort;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiArguments;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiResult;
import pl.mkn.tdw.integrations.openapi.service.OpenApiSchema;
import pl.mkn.tdw.integrations.openapi.service.OpenApiTarget;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;

@Component
public class OpenApiRestExecutor implements OpenApiExecutionPort {
    private final RestClient client;
    private final ObjectMapper mapper;

    public OpenApiRestExecutor(RestClient.Builder builder, ObjectMapper mapper) {
        this.mapper = mapper;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = builder.clone().requestFactory(factory).build();
    }

    @Override
    public OpenApiResult execute(OpenApiOperation operation, OpenApiArguments requestArguments) {
        var arguments = requestArguments.json();
        OpenApiSchema.validate(operation.validationSchema(), arguments, "arguments");
        var base = operation.server().toString().replaceAll("/+$", "");
        var uri = UriComponentsBuilder.fromUriString(base + operation.path());
        var variables = new LinkedHashMap<String, String>();
        for (var parameter : operation.parameters()) {
            var value = arguments.path(parameter.in()).get(parameter.name());
            if (value == null) continue;
            if (parameter.in().equals("path")) {
                if (value.asText().contains("/") || value.asText().contains("\\") || value.asText().contains("%"))
                    throw new OpenApiException("Parametr path musi być pojedynczym segmentem bez slash i kodowania URL.");
                if (value.asText().equals(".") || value.asText().equals(".."))
                    throw new OpenApiException("Niedozwolony segment ścieżki.");
                variables.put(parameter.name(), value.asText());
            } else {
                var key = "__query" + variables.size();
                variables.put(key, value.asText());
                uri.queryParam(parameter.name(), "{" + key + "}");
            }
        }
        var target = uri.encode().buildAndExpand(variables).toUri();
        OpenApiTarget.uri(target.getScheme() + "://" + target.getRawAuthority());
        if (!target.getAuthority().equals(operation.server().getAuthority()))
            throw new OpenApiException("Zmiana hosta jest zabroniona.");
        try {
            var request = client.method(HttpMethod.valueOf(operation.method())).uri(target).accept(MediaType.APPLICATION_JSON);
            if (arguments.has("body")) request.contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsBytes(arguments.get("body")));
            return request.exchange((sent, response) -> {
                var bytes = response.getBody().readNBytes(65_537);
                if (bytes.length > 65_536)
                    return unknown(operation, "Odpowiedź przekracza 64 KiB. Nie ustalono pełnego wyniku.");
                JsonNode body;
                try {
                    body = bytes.length == 0 ? mapper.nullNode() : mapper.readTree(bytes);
                } catch (java.io.IOException ex) {
                    return unknown(operation, "Odpowiedź nie jest poprawnym JSON. Nie ustalono wyniku biznesowego.");
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

    private OpenApiResult unknown(OpenApiOperation operation, String message) {
        return new OpenApiResult(operation.mutation() ? "OUTCOME_UNKNOWN" : "REQUEST_FAILED", null, mapper.nullNode(), message);
    }
}
