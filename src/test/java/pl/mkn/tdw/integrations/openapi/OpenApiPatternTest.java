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
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

class OpenApiPatternTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private String source() throws Exception { return Files.readString(Path.of("src/test/resources/openapi/pattern-api.yaml")); }

    @Test void preservesPatternsThroughRefsSelectionAndProjectionAndRejectsInvalidDeclarations() throws Exception {
        var selected = selection.select("demo", source(), List.of(new OpenApiSelection.OperationKey("POST", "/items")));
        var document = selection.document("demo", selected.yaml()).specification();
        assertThat(document.path("components").path("schemas").path("Code").path("pattern").asText()).isEqualTo("^[A-Z]{2}-[0-9]{3}$");
        var op = importer.parse("demo", selected.yaml()).get(0);
        var code = op.inputSchema().path("properties").path("body").path("properties").path("records").path("items")
                .path("allOf").get(0).path("properties").path("code");
        assertThat(code.path("pattern").asText()).isEqualTo("^[A-Z]{2}-[0-9]{3}$");
        assertThat(importer.parse("demo", source().replace("3.1.0", "3.0.3"))).hasSize(2);
        for (var invalid : List.of(mapper.nullNode(), mapper.valueToTree(7), mapper.valueToTree("["))) {
            var changed = (ObjectNode) selection.document("demo", source()).specification();
            ((ObjectNode) changed.path("components").path("schemas").path("Code")).set("pattern", invalid);
            assertThatThrownBy(() -> importer.parse("demo", changed.toString())).isInstanceOf(OpenApiException.class).hasMessageContaining("/pattern");
        }
        var responseOnly = source().replace("schema: {$ref: '#/components/schemas/Code'}", "schema: {type: string, pattern: '['}");
        assertThatThrownBy(() -> importer.parse("demo", responseOnly)).isInstanceOf(OpenApiException.class).hasMessageContaining("/pattern");
    }

    @Test void honorsSubstringAnchorsEscapingAndNullableWithoutRewritingTheRegex() throws Exception {
        var schema = mapper.createObjectNode().put("type", "string").put("pattern", "needle");
        OpenApiSchema.validate(schema, mapper.valueToTree("prefix-needle-suffix"), "value");
        schema.put("pattern", "^needle$");
        assertThatThrownBy(() -> OpenApiSchema.validate(schema, mapper.valueToTree("prefix-needle-suffix"), "value"))
                .isInstanceOf(OpenApiException.class).hasMessageContaining("pattern");
        schema.put("pattern", "^v[0-9]+\\.[0-9]+$").put("nullable", true);
        OpenApiSchema.check(schema, "value", 0);
        OpenApiSchema.validate(schema, mapper.valueToTree("v1.2"), "value");
        OpenApiSchema.validate(schema, mapper.nullNode(), "value");
        assertThatThrownBy(() -> OpenApiSchema.validate(schema, mapper.valueToTree("v1x2"), "value")).isInstanceOf(OpenApiException.class);
        assertThat(OpenApiSchema.toolArguments(schema).path("pattern")).isEqualTo(schema.get("pattern"));
        schema.put("pattern", "");
        OpenApiSchema.check(schema, "value", 0);
        OpenApiSchema.validate(schema, mapper.valueToTree("anything"), "value");
        OpenApiSchema.validate(mapper.readTree("{\"pattern\":\"[A-Z]+\"}"), mapper.valueToTree(7), "value");
    }

    @Test void validatesNestedBodyPathAndQueryPatternsBeforeSendingHttp() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            var bytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var operations = importer.parse("demo", source().replace(":8099", ":" + server.getAddress().getPort()));
            var executor = new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, profile -> null);
            assertThat(executor.execute(new OpenApiExecution(operations.get(0), null), args("{\"body\":{\"records\":[{\"code\":\"AB-123\"}]}}")).statusCode()).isEqualTo(200);
            assertThat(executor.execute(new OpenApiExecution(operations.get(1), null), args("{\"path\":{\"code\":\"AB-123\"},\"query\":{\"query\":\"a-needle-b\",\"tags\":[\"blue\"]}}")).statusCode()).isEqualTo(200);
            assertThatThrownBy(() -> executor.execute(new OpenApiExecution(operations.get(0), null), args("{\"body\":{\"records\":[{\"code\":\"bad\"}]}}"))).isInstanceOf(OpenApiException.class).hasMessageContaining("pattern");
            for (var invalid : List.of("{\"path\":{\"code\":\"bad\"}}", "{\"path\":{\"code\":\"AB-123\"},\"query\":{\"query\":\"absent\"}}", "{\"path\":{\"code\":\"AB-123\"},\"query\":{\"tags\":[\"INVALID\"]}}"))
                assertThatThrownBy(() -> executor.execute(new OpenApiExecution(operations.get(1), null), args(invalid))).isInstanceOf(OpenApiException.class).hasMessageContaining("pattern");
            assertThat(calls).hasValue(2);
        } finally { server.stop(0); }
    }

    private OpenApiArguments args(String json) throws Exception { return new OpenApiArguments(mapper.readTree(json)); }
}
