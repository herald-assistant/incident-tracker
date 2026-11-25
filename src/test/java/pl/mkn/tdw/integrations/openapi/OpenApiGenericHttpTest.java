package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.*;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

class OpenApiGenericHttpTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);

    private String source() throws Exception { return Files.readString(Path.of("src/test/resources/openapi/generic-http-api.yaml")); }
    private OpenApiArguments args(String value) throws Exception { return new OpenApiArguments(mapper.readTree(value)); }
    private OpenApiRestExecutor executor() {
        return new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, profile -> {
            assertThat(profile).isNotNull(); return "Bearer " + profile.token();
        });
    }
    private HttpAuthProfile auth() {
        return new HttpAuthProfile("selected", "Selected", "BEARER", null, null, "test-token", null, null, null, null, null, null, null);
    }

    @Test void selectsJsonAndYamlAndRetainsExecutableMetadataForAllFiveMethods() throws Exception {
        var json = selection.preview("generic", source()).specification().toString();
        var keys = List.of("GET", "POST", "PUT", "PATCH", "DELETE").stream()
                .map(method -> new OpenApiSelection.OperationKey(method, "/{resourcePath}")).toList();
        var prepared = selection.select("generic", json, keys);
        assertThat(prepared.operationCount()).isEqualTo(5);
        assertThat(prepared.yaml()).contains("x-tdw-catch-all: true", "explode: true", "additionalProperties: {}");
        assertThat(selection.document("generic", prepared.yaml()).specification().path("paths").size()).isEqualTo(1);
        for (var operation : importer.parse("generic", prepared.yaml())) {
            assertThat(operation.parameters()).containsExactly(
                    new OpenApiOperation.Parameter("resourcePath", "path", false, true, false),
                    new OpenApiOperation.Parameter("params", "query", true, false, true));
            assertThat(operation.inputSchema().path("properties").path("path").path("properties").path("resourcePath").path("description").asText())
                    .contains("Ścieżka względna");
            assertThat(operation.inputSchema().toString()).doesNotContain("Authorization", "test-token");
            assertThat(operation.responseContentTypes()).containsEntry("default", List.of("*/*"));
            if (!operation.method().equals("GET")) assertThat(operation.validationSchema().path("properties").path("body").isEmpty()).isTrue();
        }
    }

    @Test void executesFiveMethodsThroughCallbacksWithCapturedAuthFreeJsonAndEncodedQuery() throws Exception {
        var methods = new ArrayList<String>(); var bodies = new ArrayList<String>();
        var paths = new ArrayList<String>(); var queries = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            methods.add(exchange.getRequestMethod()); paths.add(exchange.getRequestURI().getRawPath());
            queries.add(exchange.getRequestURI().getRawQuery());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
            var bytes = "{\"custom\":[1,{\"nested\":true}],\"nullable\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var operations = importer.parse("generic", source().replace(":8099", ":" + server.getAddress().getPort()));
            var callbacks = new OpenApiToolSetFactory(executor(), mapper).create(operations.stream().map(op -> new OpenApiExecution(op, auth())).toList());
            for (int i = 0; i < callbacks.size(); i++) {
                var input = mapper.createObjectNode();
                input.putObject("path").put("resourcePath", "rest/api/3/issue/ZAŻ-1");
                input.putObject("query").putObject("params").put("jql", "project = DEMO & status != Done")
                        .put("maxResults", 20).put("validate", true).put("x&evil=1", "{resourcePath}?#");
                if (!operations.get(i).method().equals("GET")) input.set("body", mapper.readTree("{\"arbitrary\":[true,{\"a\":null}],\"text\":\"Zażółć\"}"));
                var result = mapper.readTree(callbacks.get(i).call(input.toString()));
                assertThat(result.path("statusCode").asInt()).isEqualTo(200);
                assertThat(result.path("body").path("custom").get(1).path("nested").asBoolean()).isTrue();
            }
            assertThat(methods).containsExactly("GET", "POST", "PUT", "DELETE", "PATCH");
            assertThat(paths).allMatch(path -> path.equals("/api/rest/api/3/issue/ZA%C5%BB-1"));
            assertThat(queries).allMatch(query -> query.equals("jql=project%20%3D%20DEMO%20%26%20status%20%21%3D%20Done&maxResults=20&validate=true&x%26evil%3D1=%7BresourcePath%7D%3F%23"));
            assertThat(bodies.get(0)).isEmpty();
            for (var body : bodies.subList(1, bodies.size())) assertThat(mapper.readTree(body).path("arbitrary").get(1).path("a").isNull()).isTrue();
        } finally { server.stop(0); }
    }

    @Test void rejectsPathEscapesInvalidQueryAndArgumentInjectionBeforeHttpOrAuth() throws Exception {
        var operation = importer.parse("generic", source()).get(0);
        var authCalls = new AtomicInteger();
        var executor = new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper,
                profile -> { authCalls.incrementAndGet(); return null; });
        for (var path : List.of("", "/rest/api/3", "//other.example/a", "https://other.example/a", "../a", "a/../b", "a/./b", "a%2fb", "a%252fb", "a\\b", "a?x=1", "a#b", "a\nb")) {
            var input = mapper.createObjectNode(); input.putObject("path").put("resourcePath", path);
            assertThatThrownBy(() -> executor.execute(new OpenApiExecution(operation, null), new OpenApiArguments(input))).isInstanceOf(OpenApiException.class);
        }
        for (var query : List.of("{\"a\":null}", "{\"a\":[]}", "{\"a\":{}}", "{\"\":1}", "{\"a\\n\":1}"))
            assertThatThrownBy(() -> executor.execute(new OpenApiExecution(operation, null), args("{\"path\":{\"resourcePath\":\"rest/api/3\"},\"query\":{\"params\":" + query + "}}")))
                    .isInstanceOf(OpenApiException.class);
        for (var extra : List.of("\"url\":\"https://other.example\"", "\"headers\":{\"Authorization\":\"other\"}", "\"method\":\"POST\"", "\"body\":{}"))
            assertThatThrownBy(() -> executor.execute(new OpenApiExecution(operation, null), args("{\"path\":{\"resourcePath\":\"rest/api/3\"}," + extra + "}")))
                    .isInstanceOf(OpenApiException.class);
        var normal = importer.parse("generic", source().replace("x-tdw-catch-all: true", "x-tdw-catch-all: false")).get(0);
        assertThatThrownBy(() -> executor.execute(new OpenApiExecution(normal, null), args("{\"path\":{\"resourcePath\":\"rest/api/3\"}}"))).isInstanceOf(OpenApiException.class);
        assertThat(authCalls).hasValue(0);
    }

    @Test void rejectsInvalidCatchAllAndQuerySerializationContractsAndKeepsSchemaConstraints() throws Exception {
        for (var source : List.of(source().replace("x-tdw-catch-all: true", "x-tdw-catch-all: 'true'"),
                source().replace("/{resourcePath}:", "/{resourcePath}/suffix:"),
                source().replace("/{resourcePath}:", "/{resourcePath}/{resourcePath}:"),
                source().replace("in: path", "in: query"), source().replace("explode: true", "explode: false"),
                source().replace("style: form", "style: deepObject"), source().replace("3.1.0", "3.0.3")))
            assertThatThrownBy(() -> importer.parse("generic", source)).isInstanceOf(OpenApiException.class);
        var document = (ObjectNode) selection.document("generic", source()).specification();
        var parameter = (ObjectNode) document.path("paths").path("/{resourcePath}").path("parameters").get(1);
        parameter.putObject("schema").put("type", "object").putObject("additionalProperties").put("type", "integer");
        var operation = importer.parse("generic", document.toString()).get(0);
        assertThatThrownBy(() -> executor().execute(new OpenApiExecution(operation, auth()), args("{\"path\":{\"resourcePath\":\"items\"},\"query\":{\"params\":{\"limit\":\"wrong\"}}}")))
                .isInstanceOf(OpenApiException.class);
        // A map and a named parameter cannot silently overwrite each other's serialized key.
        ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("paths").path("/{resourcePath}").path("parameters"))
                .addObject().put("name", "limit").put("in", "query").putObject("schema").put("type", "integer");
        var collision = importer.parse("generic", document.toString()).get(0);
        assertThatThrownBy(() -> executor().execute(new OpenApiExecution(collision, auth()), args("{\"path\":{\"resourcePath\":\"items\"},\"query\":{\"params\":{\"limit\":1},\"limit\":2}}")))
                .isInstanceOf(OpenApiException.class).hasMessageContaining("Powtórzona nazwa query");
    }

    @Test void wildcardResponsesPreserveJsonTextBinaryErrorsAndUnknownWriteGuard() throws Exception {
        var type = new AtomicReference<>("application/problem+json");
        var body = new AtomicReference<>("[1,true,null]".getBytes(StandardCharsets.UTF_8));
        var status = new AtomicInteger(400); var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", type.get());
            exchange.sendResponseHeaders(status.get(), body.get().length); exchange.getResponseBody().write(body.get()); exchange.close();
        });
        server.start();
        try {
            var operations = importer.parse("generic", source().replace(":8099", ":" + server.getAddress().getPort()));
            var executor = executor(); var execution = new OpenApiExecution(operations.get(0), auth());
            var args = args("{\"path\":{\"resourcePath\":\"rest/api/3\"}}");
            var result = executor.execute(execution, args);
            assertThat(result.statusCode()).isEqualTo(400); assertThat(result.body().isArray()).isTrue(); assertThat(result.failed()).isTrue();
            status.set(200); type.set("text/html; charset=ISO-8859-1"); body.set("café".getBytes(StandardCharsets.ISO_8859_1));
            assertThat(executor.execute(execution, args).body().asText()).isEqualTo("café");
            type.set("application/octet-stream"); body.set(new byte[]{0, 1, -1});
            result = executor.execute(execution, args);
            assertThat(result.body().path("encoding").asText()).isEqualTo("base64");
            assertThat(result.body().path("data").asText()).isEqualTo("AAH/");
            assertThat(result.body().path("contentType").asText()).isEqualTo("application/octet-stream");
            type.set("application/json"); body.set("broken JSON".getBytes(StandardCharsets.UTF_8));
            var callbacks = new OpenApiToolSetFactory(executor, mapper).create(operations.stream().map(op -> new OpenApiExecution(op, auth())).toList());
            var post = callbacks.stream().filter(tool -> tool.getToolDefinition().name().equals("oa_generic_genericPost")).findFirst().orElseThrow();
            assertThat(mapper.readTree(post.call(args.json().toString())).path("outcome").asText()).isEqualTo("OUTCOME_UNKNOWN");
            assertThat(mapper.readTree(post.call(args.json().toString())).path("outcome").asText()).isEqualTo("WRITE_BLOCKED");
            assertThat(mapper.readTree(callbacks.get(0).call(args.json().toString())).path("outcome").asText()).isEqualTo("REQUEST_FAILED");
            assertThat(calls).hasValue(5);
        } finally { server.stop(0); }
    }
}
