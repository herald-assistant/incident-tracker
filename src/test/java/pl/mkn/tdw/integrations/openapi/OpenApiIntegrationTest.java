package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class OpenApiIntegrationTest {
    @Test
    void reportsUnknownAfterReadTimeoutAndAllowsStateConfirmation() throws Exception {
        var writes = new AtomicInteger();
        var state = new java.util.concurrent.atomic.AtomicReference<>("OPEN");
        var workers = java.util.concurrent.Executors.newCachedThreadPool();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/tickets", exchange -> {
            try {
                if (exchange.getRequestMethod().equals("PUT")) {
                    exchange.getRequestBody().readAllBytes(); writes.incrementAndGet(); state.set("IN_PROGRESS");
                    try { Thread.sleep(20_000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                }
                var bytes = ("{\"status\":\"" + state.get() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            var operations = importer.parse("crm", fixture().replace(":8099", ":" + server.getAddress().getPort()));
            var executor = new OpenApiRestExecutor(RestClient.builder(), mapper, profile -> null);
            var write = operations.stream().filter(op -> op.method().equals("PUT")).findFirst().orElseThrow();
            var read = operations.stream().filter(op -> op.method().equals("GET")).findFirst().orElseThrow();
            var result = executor.execute(write, args("{\"path\":{\"id\":\"CRM-101\"},\"body\":{\"title\":\"Demo\",\"status\":\"IN_PROGRESS\"}}"), null);
            assertThat(result.outcome()).isEqualTo("OUTCOME_UNKNOWN");
            assertThat(executor.execute(read, args("{\"path\":{\"id\":\"CRM-101\"}}"), null).body().path("status").asText()).isEqualTo("IN_PROGRESS");
            assertThat(writes).hasValue(1);
        } finally { server.stop(0); workers.shutdownNow(); }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private String fixture() throws Exception { return Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml")); }

    @Test
    void doesNotRepeatWriteAfterConnectionDropsAndValidatesQueryEncoding() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tickets", exchange -> {
            exchange.getRequestBody().readAllBytes(); calls.incrementAndGet(); exchange.close();
        });
        var querySeen = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/lookup", exchange -> {
            querySeen.set(exchange.getRequestURI().getRawQuery());
            var bytes = "{}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var executor = new OpenApiRestExecutor(RestClient.builder(), mapper, profile -> null);
            var operations = importer.parse("crm", fixture().replace(":8099", ":" + server.getAddress().getPort()));
            var write = operations.stream().filter(op -> op.method().equals("PUT")).findFirst().orElseThrow();
            var result = executor.execute(write, args("{\"path\":{\"id\":\"CRM-101\"},\"body\":{\"title\":\"Demo\",\"status\":\"OPEN\"}}"), null);
            assertThat(result.outcome()).isEqualTo("OUTCOME_UNKNOWN"); assertThat(calls).hasValue(1);
            var queryYaml = """
                    openapi: 3.0.3
                    servers:
                      - url: http://127.0.0.1:%d
                    paths:
                      /lookup/{q0}:
                        get:
                          operationId: lookup
                          parameters:
                            - {name: search, in: query, schema: {type: string}}
                            - {name: q0, in: path, required: true, schema: {type: string}}
                          responses:
                            '200': {description: Demo, content: {application/json: {schema: {type: object}}}}
                    """.formatted(server.getAddress().getPort());
            var lookup = importer.parse("crm", queryYaml).get(0);
            executor.execute(lookup, args("{\"path\":{\"q0\":\"CRM-101\"},\"query\":{\"search\":\"A&B #1\"}}"), null);
            assertThat(querySeen.get()).isEqualTo("search=A%26B%20%231");
        } finally { server.stop(0); }
    }

    private pl.mkn.tdw.integrations.openapi.contract.OpenApiArguments args(String json) throws Exception {
        return new pl.mkn.tdw.integrations.openapi.contract.OpenApiArguments(mapper.readTree(json));
    }

    @Test
    void rejectsUnsupportedAndAmbiguousSpecifications() throws Exception {
        var yaml = fixture();
        assertThat(importer.parse("crm", yaml)).hasSize(3);
        for (var invalid : new String[]{yaml.replace("3.0.3", "3.1.0"), yaml.replace("http://127.0.0.1:8099", "file:///tmp/api"),
                yaml.replace("minLength: 1", "nullable: wrong"), yaml.replace("#/components/schemas/Ticket", "https://example.com/ticket"),
                yaml.replace("operationId: simulateCost", "operationId: simulateCost\n      servers: []"),
                yaml + "\nsecurity: [{bearer: []}]\n", yaml.replace("operationId: simulateCost", "operationId: simulateCost\n      callbacks: {}"),
                yaml.replace("maxLength: 160", "maxLength: 160, pattern: '.*'"), yaml.replace("get:", "patch:"),
                yaml.replace("minimum: 0", "minimum: 100001")})
            assertThatThrownBy(() -> importer.parse("crm", invalid)).isInstanceOf(OpenApiException.class);
        assertThatThrownBy(() -> importer.parse("crm", "openapi: 3.0.3\nopenapi: 3.0.2")).isInstanceOf(OpenApiException.class);
    }

    @Test
    void executesJsonWithoutFollowingRedirects() throws Exception {
        var calls = new AtomicInteger();
        var bodyLength = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tickets", exchange -> {
            calls.incrementAndGet();
            var response = exchange.getRequestBody().readAllBytes();
            if (response.length > 0) bodyLength.set(Integer.parseInt(exchange.getRequestHeaders().getFirst("Content-Length")));
            if (exchange.getRequestMethod().equals("PUT")) {
                assertThat(exchange.getRequestHeaders().getFirst("Content-Length")).isEqualTo(Integer.toString(response.length));
                assertThat(exchange.getRequestHeaders().getFirst("Transfer-Encoding")).isNull();
            }
            var json = response.length == 0 ? "{\"title\":\"Demo\",\"status\":\"OPEN\"}" : new String(response, StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(json.getBytes(StandardCharsets.UTF_8)); exchange.close();
        });
        server.createContext("/simulate", exchange -> {
            calls.incrementAndGet(); exchange.getResponseHeaders().set("Location", "http://example.com");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        try {
            var operations = importer.parse("crm", fixture().replace(":8099", ":" + server.getAddress().getPort()));
            var executor = new OpenApiRestExecutor(RestClient.builder(), mapper, profile -> null);
            var read = operations.stream().filter(op -> op.method().equals("GET")).findFirst().orElseThrow();
            var write = operations.stream().filter(op -> op.method().equals("PUT")).findFirst().orElseThrow();
            assertThat(executor.execute(read, args("{\"path\":{\"id\":\"CRM-101\"}}"), null).body().path("status").asText()).isEqualTo("OPEN");
            var result = executor.execute(write, args("{\"path\":{\"id\":\"CRM-101\"},\"body\":{\"title\":\"New\",\"status\":\"CLOSED\"}}"), null);
            assertThat(result.body().path("title").asText()).isEqualTo("New");
            assertThat(bodyLength).hasValue(mapper.writeValueAsBytes(result.body()).length);
            assertThatThrownBy(() -> executor.execute(write, args("{\"path\":{\"id\":\"CRM-101\"},\"body\":{\"title\":\"X\",\"status\":\"OTHER\"}}"), null))
                    .isInstanceOf(OpenApiException.class);
            var simulation = operations.stream().filter(op -> op.path().equals("/simulate")).findFirst().orElseThrow();
            assertThat(executor.execute(simulation, args("{\"body\":{\"amount\":12,\"months\":2}}"), null).statusCode()).isEqualTo(302);
            assertThat(calls).hasValue(3);
        } finally { server.stop(0); }
    }
}
