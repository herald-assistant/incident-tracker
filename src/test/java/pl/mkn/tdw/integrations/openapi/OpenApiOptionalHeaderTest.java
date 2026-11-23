package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class OpenApiOptionalHeaderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private String source() throws Exception { return Files.readString(Path.of("src/test/resources/openapi/optional-header-api.yaml")); }

    @Test void retainsDocumentationButOmitsOptionalHeadersFromExecutableParametersAndSchemas() throws Exception {
        var preview = selection.preview("items", source());
        assertThat(preview.operations().get(0).warnings()).anySatisfy(warning -> assertThat(warning).contains("correlationId", "X-Client", "Authorization", "pomijane"));
        var prepared = selection.select("items", source(), List.of(new OpenApiSelection.OperationKey("GET", "/items/{id}")));
        assertThat(prepared.yaml()).contains("correlationId", "X-Client", "Authorization", "untrusted-header-default");
        assertThat(selection.document("items", prepared.yaml()).specification().path("paths").path("/items/{id}").path("parameters")).hasSize(3);
        var operation = importer.parse("items", prepared.yaml()).get(0);
        assertThat(operation.parameters()).containsExactly(new OpenApiOperation.Parameter("id", "path", false));
        assertThat(operation.inputSchema().toString()).doesNotContain("correlationId", "X-Client", "Authorization", "untrusted");
        assertThat(operation.validationSchema().toString()).doesNotContain("correlationId", "X-Client", "Authorization", "untrusted");
        assertThat(operation.description()).contains("pomijane").doesNotContain("untrusted-header-default", "untrusted-authorization-default");
    }

    @Test void rejectsRequiredHeadersMalformedRequiredAndCookies() throws Exception {
        for (var required : List.of("true", "'false'", "null")) {
            var changed = source().replace("name: correlationId, in: header", "name: correlationId, in: header, required: " + required);
            assertThatThrownBy(() -> selection.select("items", changed, List.of(new OpenApiSelection.OperationKey("GET", "/items/{id}"))))
                    .isInstanceOf(OpenApiException.class).hasMessageContaining(required.equals("true") ? "correlationId" : "required");
        }
        assertThatThrownBy(() -> importer.parse("items", source().replace("name: correlationId, in: header", "name: correlationId, in: cookie")))
                .isInstanceOf(OpenApiException.class);
    }

    @Test void appliesHeaderOverridesCaseInsensitivelyAndRejectsDuplicates() throws Exception {
        var document = selection.document("items", source()).specification();
        var path = (ObjectNode) document.path("paths").path("/items/{id}");
        ((ObjectNode) path.path("parameters").get(1)).put("required", true);
        var overrides = (ArrayNode) path.path("get").path("parameters");
        overrides.addObject().put("name", "CORRELATIONID").put("in", "header").put("required", false);
        assertThat(importer.parse("items", document.toString()).get(0).parameters()).hasSize(1);
        assertThat(selection.preview("items", document.toString()).operations().get(0).warnings())
                .anySatisfy(warning -> assertThat(warning).contains("CORRELATIONID").doesNotContain("correlationId"));
        ((ObjectNode) overrides.get(1)).put("required", true);
        assertThatThrownBy(() -> importer.parse("items", document.toString())).isInstanceOf(OpenApiException.class).hasMessageContaining("CORRELATIONID");
        ((ObjectNode) overrides.get(1)).put("required", false);
        overrides.addObject().put("name", "CorrelationId").put("in", "header");
        assertThatThrownBy(() -> importer.parse("items", document.toString())).isInstanceOf(OpenApiException.class).hasMessageContaining("Powtórzony");
    }

    @Test void sendsNoDeclaredHeaderAndBlocksInjectedArgumentsBeforeHttpWhileRetainingProfileAuth() throws Exception {
        var calls = new AtomicInteger();
        var received = new AtomicReference<Map<String, List<String>>>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items/7", exchange -> {
            calls.incrementAndGet(); received.set(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
            received.get().putAll(exchange.getRequestHeaders());
            var bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var operation = importer.parse("items", source().replace(":8099", ":" + server.getAddress().getPort())).get(0);
            var profile = mapper.readValue("{\"id\":\"test\",\"name\":\"Test\",\"type\":\"BEARER\",\"token\":\"profile-token\"}", pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile.class);
            var executor = new OpenApiRestExecutor(RestClient.builder(), mapper, selected -> "Bearer " + selected.token());
            assertThat(executor.execute(new OpenApiExecution(operation, profile), new OpenApiArguments(mapper.readTree("{\"path\":{\"id\":7}}"))).statusCode()).isEqualTo(200);
            assertThat(received.get()).doesNotContainKeys("correlationId", "X-Client");
            assertThat(received.get().get("Authorization")).containsExactly("Bearer profile-token");
            for (var injection : List.of("\"headers\":{\"correlationId\":\"model-value\"}", "\"header\":{\"Authorization\":\"model-value\"}", "\"correlationId\":\"model-value\""))
                assertThatThrownBy(() -> executor.execute(new OpenApiExecution(operation, profile), new OpenApiArguments(mapper.readTree("{\"path\":{\"id\":7}," + injection + "}"))))
                        .isInstanceOf(OpenApiException.class);
            assertThat(calls).hasValue(1);
        } finally { server.stop(0); }
    }
}
