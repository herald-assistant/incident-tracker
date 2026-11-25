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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

class OpenApiQueryArrayTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private String source() throws Exception { return Files.readString(Path.of("src/test/resources/openapi/query-arrays-api.yaml")); }

    @Test void selectsRefsAndOverridesExplodeWithoutChangingTheSourceSchema() throws Exception {
        var document = (ObjectNode) selection.document("items", source()).specification();
        var item = (ObjectNode) document.path("paths").path("/items/{id}");
        ((ObjectNode) item.path("get")).withArray("parameters").addObject()
                .put("name", "ids").put("in", "query").put("required", true).put("style", "form").put("explode", false)
                .putObject("schema").put("$ref", "#/components/schemas/Ids");
        var prepared = selection.select("items", document.toString(), List.of(new OpenApiSelection.OperationKey("GET", "/items/{id}")));
        assertThat(prepared.yaml()).contains("#/components/schemas/Ids", "uniqueItems", "explode: false").doesNotContain("/csv:");
        var operation = importer.parse("items", prepared.yaml()).get(0);
        assertThat(operation.parameters()).contains(new OpenApiOperation.Parameter("ids", "query", false, false, false), new OpenApiOperation.Parameter("tags", "query", true, false, false));
        assertThat(operation.parameters().stream().filter(p -> p.name().equals("ids"))).hasSize(1);
        var schema = operation.inputSchema().path("properties").path("query").path("properties").path("ids");
        assertThat(schema.path("type").asText()).isEqualTo("array");
        assertThat(schema.path("items").path("type").asText()).isEqualTo("integer");
        assertThat(schema.path("uniqueItems").asBoolean()).isTrue();
        assertThat(schema.path("minItems").asInt()).isEqualTo(1);
        assertThat(schema.path("maxItems").asInt()).isEqualTo(3);
        assertThat(selection.document("items", prepared.yaml()).specification().path("components").path("schemas").path("Ids").has("description")).isFalse();
        assertThat(importer.parse("items", source().replace("3.1.0", "3.0.3"))).hasSize(3);
    }

    @Test void sendsRepeatedAndCsvValuesWithPerElementEncodingAndValidatesBeforeHttp() throws Exception {
        var query = new AtomicReference<String>(); var path = new AtomicReference<String>();
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); query.set(exchange.getRequestURI().getRawQuery()); path.set(exchange.getRequestURI().getPath());
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            var bytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var operations = importer.parse("items", source().replace(":8099", ":" + server.getAddress().getPort()));
            var executor = new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, profile -> null);
            var repeated = operations.get(0);
            var arguments = args("""
                    {"path":{"id":"A B"},"query":{"ids":[101,102],"tags":["A&B #1","a,b","x+y","%2F","{id}","Zażółć",""],"flags":[true,false],"weights":[1.25,-2],"statuses":["NEW"]}}
                    """);
            assertThat(executor.execute(new OpenApiExecution(repeated, null), arguments).statusCode()).isEqualTo(200);
            assertThat(path).hasValue("/items/A B");
            assertThat(query.get()).isEqualTo("ids=101&ids=102&tags=A%26B%20%231&tags=a%2Cb&tags=x%2By&tags=%252F&tags=%7Bid%7D&tags=Za%C5%BC%C3%B3%C5%82%C4%87&tags=&flags=true,false&weights=1.25,-2&statuses=NEW");
            var csv = operations.get(1);
            executor.execute(new OpenApiExecution(csv, null), args("{\"query\":{\"values\":[\"A,B\",\"x&evil=1\",\"x+y\",\"\"]}}"));
            assertThat(query).hasValue("values=A%2CB,x%26evil%3D1,x%2By,");
            executor.execute(new OpenApiExecution(operations.get(2), null), args("{}"));
            assertThat(query.get()).isNull();
            for (var invalid : List.of("{\"ids\":[1,1]}", "{\"ids\":[1,2,3,4]}", "{\"ids\":[]}", "{\"ids\":[0]}", "{\"ids\":[\"1\"]}", "{\"ids\":[null]}", "{\"ids\":1}", "{\"ids\":[{}]}", "{\"ids\":[[1]]}", "{\"ids\":[1],\"statuses\":[\"WRONG\"]}", "{}"))
                assertThatThrownBy(() -> executor.execute(new OpenApiExecution(repeated, null), args("{\"path\":{\"id\":\"7\"},\"query\":" + invalid + "}"))).isInstanceOf(OpenApiException.class);
            for (var op : List.of(csv, operations.get(2))) {
                var name = op == csv ? "values" : "ids";
                assertThatThrownBy(() -> executor.execute(new OpenApiExecution(op, null), args("{\"query\":{\"" + name + "\":[]}}")))
                        .isInstanceOf(OpenApiException.class).hasMessageContaining("pusta tablica");
            }
            assertThat(calls).hasValue(3);
        } finally { server.stop(0); }
    }

    @Test void rejectsObjectsNestedArraysNullableItemsAndUnsupportedSerialization() throws Exception {
        for (var schema : List.of("{\"type\":\"object\"}", "{\"type\":\"array\",\"items\":{\"type\":\"object\"}}", "{\"type\":\"array\",\"items\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}", "{\"type\":\"array\",\"items\":{\"type\":[\"string\",\"null\"]}}", "{\"type\":\"array\"}")) {
            var document = (ObjectNode) selection.document("items", source()).specification();
            ((ObjectNode) document.path("paths").path("/csv").path("get").path("parameters").get(0)).set("schema", mapper.readTree(schema));
            assertThatThrownBy(() -> importer.parse("items", document.toString())).isInstanceOf(OpenApiException.class);
        }
        for (var style : List.of("deepObject", "spaceDelimited", "pipeDelimited"))
            assertThatThrownBy(() -> importer.parse("items", source().replace("style: form", "style: " + style))).isInstanceOf(OpenApiException.class).hasMessageContaining("style");
        assertThatThrownBy(() -> importer.parse("items", source().replace("explode: false", "explode: 'false'"))).isInstanceOf(OpenApiException.class).hasMessageContaining("explode");
        assertThatThrownBy(() -> importer.parse("items", source().replace("3.1.0", "3.0.3").replace("items: {type: string}", "items: {type: string, nullable: true}"))).isInstanceOf(OpenApiException.class);
        assertThatThrownBy(() -> importer.parse("items", source().replace("name: values, in: query", "name: values, in: path"))).isInstanceOf(OpenApiException.class);
    }

    private OpenApiArguments args(String json) throws Exception { return new OpenApiArguments(mapper.readTree(json)); }
}
