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
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

class OpenApiCompositionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private String fixture(String name) throws Exception { return Files.readString(Path.of("src/test/resources/openapi/" + name)); }

    @Test void preservesCompositionAndRejectsAmbiguousVariantsWithoutInventingAMapping() throws Exception {
        var source = (ObjectNode) selection.document("products", fixture("composition-api.yaml")).specification();
        ((ObjectNode) source.path("paths").path("/items/{batchId}").path("post").path("requestBody")
                .path("content").path("application/json").path("schema").path("discriminator")).remove("mapping");
        for (var variant : List.of("TimedForm", "BasicForm"))
            ((ObjectNode) source.path("components").path("schemas").path(variant).path("allOf").get(1).path("properties")).remove("type");
        source.withObject("paths").putObject("/unselected").putObject("head").put("operationId", "unsupported");
        var preview = selection.preview("products", source.toString());
        assertThat(preview.operations()).hasSize(3);
        assertThat(preview.operations().stream().filter(op -> op.operationId().equals("createItem")).findFirst().orElseThrow().warnings())
                .anyMatch(warning -> warning.contains("mapping"));
        var prepared = selection.select("products", source.toString(), List.of(
                new OpenApiSelection.OperationKey("POST", "/items/{batchId}"),
                new OpenApiSelection.OperationKey("GET", "/collections")));
        assertThat(prepared.operationCount()).isEqualTo(2);
        var document = selection.document("products", prepared.yaml()).specification();
        assertThat(document.path("openapi").asText()).isEqualTo("3.1.0");
        assertThat(document.path("paths").has("/unselected")).isFalse();
        assertThat(document.path("components").path("schemas")).hasSize(4);
        assertThat(prepared.yaml()).contains("x-test-label", "allOf", "oneOf", "discriminator");
        var operations = importer.parse("products", prepared.yaml());
        var add = operations.stream().filter(op -> op.method().equals("POST")).findFirst().orElseThrow();
        assertThat(add.inputSchema().toString()).hasSizeLessThan(OpenApiSelection.MAX_TOOL_SCHEMA_CHARACTERS);
        assertThatThrownBy(() -> OpenApiSchema.validate(add.validationSchema(), mapper.readTree(
                "{\"path\":{\"batchId\":1},\"query\":{\"ownerKey\":\"test\"},\"body\":{\"type\":\"TIMED\",\"size\":1,\"duration\":1}}"), "arguments"))
                .isInstanceOf(OpenApiException.class).hasMessageContaining("oneOf");
        assertThat(operations.stream().filter(op -> op.method().equals("GET")).findFirst().orElseThrow().mutation()).isTrue();
    }

    @Test void validatesVariantsIntersectionsMapsAndReadonlyBeforeHttp() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items/1", exchange -> {
            calls.incrementAndGet(); var bytes = "{\"id\":123}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var operation = importer.parse("products", fixture("composition-api.yaml").replace(":8099", ":" + server.getAddress().getPort())).get(0);
            var executor = new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, profile -> null);
            for (var body : List.of("{\"type\":\"TIMED\",\"size\":1,\"duration\":12,\"note\":null,\"weights\":{\"blue\":2},\"labels\":[\"a\"]}", "{\"type\":\"BASIC\",\"size\":1}"))
                assertThat(executor.execute(new OpenApiExecution(operation, null), args(body)).body().path("id").asInt()).isEqualTo(123);
            for (var invalid : List.of("{\"type\":\"TIMED\",\"size\":0,\"duration\":12}", "{\"type\":\"TIMED\",\"size\":1}",
                    "{\"type\":\"UNKNOWN\",\"size\":1}", "{\"type\":\"BASIC\",\"size\":1,\"generatedId\":1}",
                    "{\"type\":\"BASIC\",\"size\":1,\"labels\":[\"a\",\"a\"]}", "{\"type\":\"BASIC\",\"size\":1,\"weights\":{\"blue\":-1}}"))
                assertThatThrownBy(() -> executor.execute(new OpenApiExecution(operation, null), args(invalid))).isInstanceOf(OpenApiException.class);
            assertThat(calls).hasValue(2);
            assertThat(operation.inputSchema().toString()).doesNotContain("generatedId", "nullable").contains("allOf", "oneOf");
        } finally { server.stop(0); }
    }

    private OpenApiArguments args(String body) throws Exception {
        return new OpenApiArguments(mapper.readTree("{\"path\":{\"batchId\":1},\"query\":{\"ownerKey\":\"test\"},\"body\":" + body + "}"));
    }

    @Test void preserves31RefSiblingsAndRejectsExternalMissingAndRecursiveRequestReferences() throws Exception {
        var source = fixture("composition-api.yaml");
        var sibling = source.replace("- {$ref: '#/components/schemas/BaseItemForm'}", "- {$ref: '#/components/schemas/BaseItemForm', description: inherited}");
        assertThat(importer.parse("products", sibling)).hasSize(2);
        assertThatThrownBy(() -> importer.parse("products", sibling.replace("3.1.0", "3.0.3"))).isInstanceOf(OpenApiException.class).hasMessageContaining("3.0");
        for (var ref : List.of("https://example.invalid/form", "file:///etc/form", "#/components/schemas/Missing", "#/components/schemas/Node"))
            assertThatThrownBy(() -> importer.parse("products", source.replace("#/components/schemas/BaseItemForm", ref))).isInstanceOf(OpenApiException.class);
        assertThat(importer.parse("products", source).get(1).inputSchema().toString()).doesNotContain("Node", "allOf", "oneOf");
    }

    @Test void honorsAnyOfAndRejectsOverlappingOneOfWithoutGuessingAMapping() throws Exception {
        var overlapping = mapper.readTree("{\"oneOf\":[{\"type\":\"number\"},{\"type\":\"integer\"}]}");
        OpenApiSchema.validate(overlapping, mapper.readTree("1.5"), "body");
        assertThatThrownBy(() -> OpenApiSchema.validate(overlapping, mapper.readTree("1"), "body")).isInstanceOf(OpenApiException.class);
        OpenApiSchema.validate(mapper.readTree("{\"anyOf\":[{\"type\":\"number\"},{\"type\":\"integer\"}]}"), mapper.readTree("1"), "body");
        var inheritedReadOnly = mapper.readTree("""
                {"allOf":[{"type":"object","properties":{"id":{"allOf":[{"type":"integer","readOnly":true}]}}},
                 {"type":"object","required":["id"]}]}
                """);
        OpenApiSchema.validate(inheritedReadOnly, mapper.readTree("{}"), "body");
        assertThat(OpenApiSchema.toolArguments(inheritedReadOnly).toString()).doesNotContain("\"id\"");
        assertThatThrownBy(() -> OpenApiSchema.validate(inheritedReadOnly, mapper.readTree("{\"id\":1}"), "body")).isInstanceOf(OpenApiException.class);
    }

    @Test void boundsToolProjectionAndExponentialLocalReferenceExpansion() throws Exception {
        var source = (ObjectNode) selection.document("products", fixture("composition-api.yaml")).specification();
        var body = (ObjectNode) source.path("paths").path("/items/{batchId}").path("post")
                .path("requestBody").path("content").path("application/json").path("schema");
        body.put("description", "x".repeat(OpenApiSelection.MAX_TOOL_SCHEMA_CHARACTERS));
        assertThatThrownBy(() -> importer.parse("products", source.toString())).isInstanceOf(OpenApiException.class).hasMessageContaining("250000");
        var expansion = (ObjectNode) mapper.readTree("""
                {"openapi":"3.1.0","servers":[{"url":"http://127.0.0.1:8099"}],"paths":{"/expand":{"post":{
                 "operationId":"expand","requestBody":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/N0"}}}},
                 "responses":{"200":{"description":"Test"}}}}},"components":{"schemas":{}}}
                """);
        var schemas = (ObjectNode) expansion.path("components").path("schemas");
        for (int i = 0; i < 15; i++) {
            var node = schemas.putObject("N" + i).put("type", "object");
            if (i < 14) {
                var properties = node.putObject("properties");
                properties.putObject("left").put("$ref", "#/components/schemas/N" + (i + 1));
                properties.putObject("right").put("$ref", "#/components/schemas/N" + (i + 1));
            }
        }
        assertThatThrownBy(() -> importer.parse("products", expansion.toString())).isInstanceOf(OpenApiException.class).hasMessageContaining("30000");
    }
}
