package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class OpenApiCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private String fixture(String name) throws Exception { return Files.readString(Path.of("src/test/resources/openapi/" + name + "-api.yaml")); }

    @Test void importsSelectsAndDocumentsBothUserFixturesWithoutDroppingAnnotations() throws Exception {
        for (var name : List.of("products", "orders")) {
            var source = fixture(name); var preview = selection.preview(name, source);
            var selected = selection.select(name, source, preview.operations().stream().map(OpenApiSelection.Candidate::key).toList());
            assertThat(selected.operationCount()).isEqualTo(3);
            assertThat(selection.document(name, selected.yaml()).specification().path("paths").size()).isEqualTo(2);
            var operations = importer.parse(name, selected.yaml());
            if (name.equals("products")) {
                assertThat(selected.yaml()).contains("readOnly: true");
                var create = operations.stream().filter(op -> op.method().equals("POST")).findFirst().orElseThrow();
                assertThat(create.inputSchema().path("properties").path("body").path("properties").has("id")).isFalse();
                assertThat(create.validationSchema().path("properties").path("body").path("properties").path("id").path("readOnly").asBoolean()).isTrue();
                ((ObjectNode) create.validationSchema()).removeAll(); ((ObjectNode) create.inputSchema()).removeAll();
                assertThat(create.validationSchema().has("properties")).isTrue(); assertThat(create.inputSchema().has("properties")).isTrue();
            } else {
                assertThat(selected.yaml()).contains("nullable: true", "format: email", "format: uuid");
                assertThat(operations.get(0).server().getHost()).isEqualTo("localhost");
            }
        }
    }

    @Test void acceptsHttpTargetsWithoutHostRestrictionsAndRejectsMalformedBaseUrls() {
        for (var url : List.of("http://localhost:8081", "https://jira.example.com/rest/api/3", "https://api.example.net",
                "http://192.0.2.20:8080/api", "http://127.0.0.1", "http://[::1]:8099"))
            assertThat(OpenApiTarget.uri(url).toString()).isEqualTo(url);
        assertThat(OpenApiTarget.uri("HTTPS://jira.example.com/rest/api/3").toString()).isEqualTo("https://jira.example.com/rest/api/3");
        assertThatThrownBy(() -> OpenApiTarget.uri(null)).isInstanceOf(OpenApiException.class);
        for (var url : List.of("file:///tmp/api", "/relative", "http:///api", "http://user:pass@example.com", "https://api.example.com?token=a",
                "https://api.example.com#fragment", "http://example.com:99999", "http://bad host"))
            assertThatThrownBy(() -> OpenApiTarget.uri(url)).isInstanceOf(OpenApiException.class);
    }

    @Test void readOnlyOutsidePropertyDefinitionsDoesNotHideBodyOrPathArguments() throws Exception {
        var document = selection.document("products", fixture("products")).specification();
        ((ObjectNode) document.path("components").path("schemas").path("Product")).put("readOnly", true);
        ((ObjectNode) document.path("paths").path("/products/{productId}").path("get").path("parameters").get(0).path("schema")).put("readOnly", true);
        var operations = importer.parse("products", document.toString());
        var create = operations.stream().filter(op -> op.method().equals("POST")).findFirst().orElseThrow();
        var read = operations.stream().filter(op -> op.path().equals("/products/{productId}")).findFirst().orElseThrow();
        assertThat(create.inputSchema().path("properties").has("body")).isTrue();
        assertThat(create.inputSchema().path("properties").path("body").path("properties").has("id")).isFalse();
        assertThat(read.inputSchema().path("properties").path("path").path("properties").has("productId")).isTrue();
        OpenApiSchema.validate(create.validationSchema(), mapper.readTree("{\"body\":{\"name\":\"Product\",\"price\":1}}"), "arguments");
        OpenApiSchema.validate(read.validationSchema(), mapper.readTree("{\"path\":{\"productId\":\"p1\"}}"), "arguments");
    }

    @Test void executesThroughLocalhostPreservingJsonNullAndBlocksReadOnlyBeforeHttp() throws Exception {
        var received = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var body = "{\"id\":\"order-1\",\"status\":\"NEW\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var executor = new OpenApiRestExecutor(RestClient.builder(), mapper);
            var order = importer.parse("orders", fixture("orders").replace(":8081", ":" + server.getAddress().getPort())).get(0);
            var arguments = new OpenApiArguments(mapper.readTree("{\"body\":{\"customerEmail\":\"tester@example.com\",\"items\":[{\"productId\":\"p1\",\"quantity\":2}],\"note\":null}}"));
            assertThat(executor.execute(order, arguments).statusCode()).isEqualTo(201);
            assertThat(mapper.readTree(received.get()).has("note")).isTrue(); assertThat(mapper.readTree(received.get()).get("note").isNull()).isTrue();
            var product = importer.parse("products", fixture("products")).stream().filter(op -> op.method().equals("POST")).findFirst().orElseThrow();
            assertThatThrownBy(() -> executor.execute(product, new OpenApiArguments(mapper.readTree("{\"body\":{\"id\":\"forbidden\",\"name\":\"Product\",\"price\":1}}"))))
                    .isInstanceOf(OpenApiException.class).hasMessageContaining("readOnly");
        } finally { server.stop(0); }
    }
}
