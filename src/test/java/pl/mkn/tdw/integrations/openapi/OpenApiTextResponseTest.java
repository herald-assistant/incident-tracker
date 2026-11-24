package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

class OpenApiTextResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private final AtomicReference<String> accept = new AtomicReference<>();
    private HttpServer server;
    private List<OpenApiOperation> operations;
    private final OpenApiRestExecutor executor = new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, profile -> null);
    private record Reply(int status, String contentType, byte[] bytes) {}

    private String source() throws Exception { return Files.readString(Path.of("src/test/resources/openapi/text-responses-api.yaml")); }
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            var current = reply.get();
            if (current.contentType() != null) exchange.getResponseHeaders().set("Content-Type", current.contentType());
            boolean noContent = current.status() == 204 || current.status() == 205;
            exchange.sendResponseHeaders(current.status(), noContent ? -1 : current.bytes().length);
            if (!noContent) exchange.getResponseBody().write(current.bytes());
            exchange.close();
        });
        server.start();
        operations = importer.parse("demo", source().replace(":8099", ":" + server.getAddress().getPort()));
    }
    @AfterEach void stop() { server.stop(0); }
    private OpenApiResult call(String name, int status, String type, byte[] bytes) {
        reply.set(new Reply(status, type, bytes));
        var operation = operations.stream().filter(op -> op.name().endsWith("_" + name)).findFirst().orElseThrow();
        return executor.execute(new OpenApiExecution(operation, null), new OpenApiArguments(mapper.createObjectNode()));
    }
    private OpenApiResult call(String name, int status, String type, String text) {
        return call(name, status, type, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test void selectsPlainSchemaRefsAndKeepsRequestBodiesJsonOnly() throws Exception {
        var selected = selection.select("demo", source(), List.of(new OpenApiSelection.OperationKey("GET", "/state")));
        assertThat(selected.yaml()).contains("text/plain", "Message").doesNotContain("/json:", "/mixed:");
        var operation = importer.parse("demo", selected.yaml()).get(0);
        assertThat(operation.responseContentTypes()).containsEntry("200", List.of("text/plain")).containsEntry("201", List.of());
        assertThatThrownBy(() -> operation.responseContentTypes().put("200", List.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThat(importer.parse("demo", source().replace("3.1.0", "3.0.3"))).hasSize(3);
        for (var invalid : List.of(source().replace("type: string", "type: integer"), source().replace("text/plain:", "text/html:")))
            assertThatThrownBy(() -> importer.parse("demo", invalid)).isInstanceOf(OpenApiException.class);
        var document = (ObjectNode) selection.document("demo", source()).specification();
        ((ObjectNode) document.path("paths").path("/json").path("post")).putObject("requestBody").putObject("content")
                .putObject("text/plain").putObject("schema").put("type", "string");
        assertThatThrownBy(() -> importer.parse("demo", document.toString())).isInstanceOf(OpenApiException.class);
    }

    @Test void preservesTextJsonCharsetEmptyBodiesAndDefaultResponses() {
        for (var text : List.of("Zażółć\n", "true", "{\"state\":\"READY\"}", "")) {
            var result = call("readState", 200, "text/plain", text);
            assertThat(result.outcome()).isEqualTo("RESPONSE_RECEIVED");
            assertThat(result.body().isTextual()).isTrue();
            assertThat(result.body().asText()).isEqualTo(text);
            assertThat(accept).hasValue("text/plain");
        }
        assertThat(call("readState", 202, "text/plain; charset=ISO-8859-1", "café".getBytes(StandardCharsets.ISO_8859_1)).body().asText()).isEqualTo("café");
        assertThat(call("readMixed", 200, "application/json", "true").body().isBoolean()).isTrue();
        assertThat(accept).hasValue("application/json, text/plain");
        assertThat(call("writeJson", 200, null, "{}").body().isObject()).isTrue();
        assertThat(accept).hasValue("application/json");
        assertThat(call("readState", 204, "text/plain", "").body().isNull()).isTrue();
    }

    @Test void preservesFullTextAndJsonResponsesLargerThan64KiB() throws Exception {
        var text = "ą".repeat(70_000) + " KONIEC_ODPOWIEDZI";
        var plain = call("readState", 200, "text/plain", text);
        assertThat(plain.outcome()).isEqualTo("RESPONSE_RECEIVED");
        assertThat(plain.body().asText()).isEqualTo(text);
        var json = mapper.createObjectNode().put("value", text).put("lastField", "READY");
        var result = call("writeJson", 200, "application/json", mapper.writeValueAsBytes(json));
        assertThat(result.outcome()).isEqualTo("RESPONSE_RECEIVED");
        assertThat(result.body()).isEqualTo(json);
    }

    @Test void rejectsInvalidFormatsWithoutTreatingBrokenJsonAsText() {
        assertThat(call("writeJson", 200, "application/json", "broken").outcome()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(call("writeJson", 200, "application/json", " \n ").outcome()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(call("writeJson", 200, "text/plain", "READY").outcome()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(call("readState", 201, "text/plain", "undeclared").outcome()).isEqualTo("REQUEST_FAILED");
        assertThat(call("readState", 200, "text/html", "{}").outcome()).isEqualTo("REQUEST_FAILED");
        assertThat(call("readState", 200, "text/plain; charset=not-a-charset", "x").outcome()).isEqualTo("REQUEST_FAILED");
        assertThat(call("readState", 200, "text/plain", new byte[]{(byte) 0xC3, 0x28}).outcome()).isEqualTo("REQUEST_FAILED");
    }
}
