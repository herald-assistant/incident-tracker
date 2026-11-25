package pl.mkn.tdw.features.systemassistant;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import pl.mkn.tdw.agenttools.openapi.mcp.OpenApiMcpEndpoint;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "system-assistant.enabled=true", "analysis.ai.copilot.telemetry.enabled=false"
})
@DirtiesContext
class SystemAssistantMcpWireTest {
    @Test
    void exposesAndExecutesGenericPathsQueryAndJsonThroughMcp() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var query = new java.util.concurrent.atomic.AtomicReference<String>();
        var method = new java.util.concurrent.atomic.AtomicReference<String>();
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/api/rest/api/3/issue/DEMO-1", exchange -> {
            calls.incrementAndGet(); query.set(exchange.getRequestURI().getRawQuery()); method.set(exchange.getRequestMethod());
            var bytes = exchange.getRequestBody().readAllBytes();
            if (bytes.length == 0) bytes = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var source = Files.readString(Path.of("src/test/resources/openapi/generic-http-api.yaml"))
                    .replace(":8099", ":" + api.getAddress().getPort());
            configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("demo", "Demo", source)), List.of()));
            configurations.load();
            assertThat(configurations.current().apis().get(0).yaml()).contains("x-tdw-catch-all: true");
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var tools = client.listTools().tools();
                assertThat(tools).hasSize(5);
                var schema = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(tools.stream()
                        .filter(tool -> tool.name().equals("oa_demo_genericGet")).findFirst().orElseThrow().inputSchema());
                assertThat(schema.path("properties").path("query").path("properties").path("params").path("type").asText()).isEqualTo("object");
                var args = Map.<String, Object>of("path", Map.of("resourcePath", "rest/api/3/issue/DEMO-1"),
                        "query", Map.of("params", Map.of("fields", "summary,status")), "body", Map.of("arbitrary", List.of(1, 2, 3)));
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_demo_genericPatch", args)).isError()).isFalse();
                assertThat(method).hasValue("PATCH"); assertThat(query).hasValue("fields=summary%2Cstatus");
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_demo_genericGet", Map.of("path", Map.of("resourcePath", "../outside")))).isError()).isTrue();
                assertThat(calls).hasValue(1);
            }
        } finally { api.stop(0); configurations.save(SystemAssistantConfiguration.empty()); }
    }

    @Test
    void exposesNestedPatternsAndRejectsViolationsBeforeHttpThroughMcp() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/items", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            var bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var source = Files.readString(Path.of("src/test/resources/openapi/pattern-api.yaml"))
                    .replace(":8099", ":" + api.getAddress().getPort());
            configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("demo", "Demo", source)), List.of()));
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var tool = client.listTools().tools().stream().filter(item -> item.name().equals("oa_demo_createItems")).findFirst().orElseThrow();
                var code = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(tool.inputSchema()).path("properties")
                        .path("body").path("properties").path("records").path("items").path("allOf").get(0).path("properties").path("code");
                assertThat(code.path("pattern").asText()).isEqualTo("^[A-Z]{2}-[0-9]{3}$");
                assertThat(client.callTool(new McpSchema.CallToolRequest(tool.name(), Map.of("body", Map.of("records", List.of(Map.of("code", "AB-123")))))).isError()).isFalse();
                assertThat(client.callTool(new McpSchema.CallToolRequest(tool.name(), Map.of("body", Map.of("records", List.of(Map.of("code", "invalid")))))).isError()).isTrue();
                assertThat(calls).hasValue(1);
            }
        } finally { api.stop(0); configurations.save(SystemAssistantConfiguration.empty()); }
    }

    @Test
    void transportsPlainTextAndKeepsTheWriteGuardForInvalidJson() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var accept = new java.util.concurrent.atomic.AtomicReference<String>();
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/", exchange -> {
            calls.incrementAndGet(); accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            boolean text = exchange.getRequestURI().getPath().equals("/state");
            var bytes = (text ? "Zażółć" : "broken").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", text ? "text/plain; charset=UTF-8" : "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var source = Files.readString(Path.of("src/test/resources/openapi/text-responses-api.yaml"))
                    .replace(":8099", ":" + api.getAddress().getPort());
            configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("demo", "Demo", source)), List.of()));
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var result = client.callTool(new McpSchema.CallToolRequest("oa_demo_readState", Map.of()));
                assertThat(result.isError()).isFalse();
                var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(((McpSchema.TextContent) result.content().get(0)).text()).path("body");
                assertThat(body.isTextual()).isTrue(); assertThat(body.asText()).isEqualTo("Zażółć");
                assertThat(accept).hasValue("text/plain");
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_demo_writeJson", Map.of())).content().toString()).contains("OUTCOME_UNKNOWN");
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_demo_writeJson", Map.of())).content().toString()).contains("WRITE_BLOCKED");
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_demo_readState", Map.of())).isError()).isFalse();
                assertThat(calls).hasValue(3);
            }
        } finally { api.stop(0); configurations.save(SystemAssistantConfiguration.empty()); }
    }

    @Test
    void exposesArrayQueryArgumentsAndSerializesRepeatedAndCsvThroughMcp() throws Exception {
        var queries = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            var bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var source = Files.readString(Path.of("src/test/resources/openapi/query-arrays-api.yaml"))
                    .replace(":8099", ":" + api.getAddress().getPort());
            configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("items", "Items", source)), List.of()));
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var tool = client.listTools().tools().stream().filter(item -> item.name().equals("oa_items_listItems")).findFirst().orElseThrow();
                var schema = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(tool.inputSchema());
                var ids = schema.path("properties").path("query").path("properties").path("ids");
                assertThat(ids.path("type").asText()).isEqualTo("array");
                assertThat(ids.path("items").path("type").asText()).isEqualTo("integer");
                var arguments = Map.<String, Object>of("path", Map.of("id", "7"), "query", Map.of("ids", List.of(101, 102), "tags", List.of("A&B", "x+y")));
                assertThat(client.callTool(new McpSchema.CallToolRequest(tool.name(), arguments)).isError()).isFalse();
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_items_listCsv", Map.of("query", Map.of("values", List.of("A,B", "C"))))).isError()).isFalse();
                assertThat(queries).containsExactly("ids=101&ids=102&tags=A%26B&tags=x%2By", "values=A%2CB,C");
                assertThat(client.callTool(new McpSchema.CallToolRequest(tool.name(), Map.of("path", Map.of("id", "7"), "query", Map.of("ids", List.of(1, 1))))).isError()).isTrue();
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_items_listCsv", Map.of("query", Map.of("values", List.of())))).isError()).isTrue();
                assertThat(queries).hasSize(2);
            }
        } finally { api.stop(0); configurations.save(SystemAssistantConfiguration.empty()); }
    }

    @Test
    void publishesShortenedNamesAndNullableArgumentsAndCallsAnApiThroughLocalhost() throws Exception {
        var operationId = "createOrder" + "WithProductPricingDetails".repeat(6);
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        var authorization = new java.util.concurrent.atomic.AtomicReference<String>();
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/orders", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = "{\"id\":\"order-1\",\"status\":\"NEW\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var yaml = Files.readString(Path.of("src/test/resources/openapi/orders-api.yaml"))
                    .replace("operationId: createOrder", "operationId: " + operationId)
                    .replace(":8081", ":" + api.getAddress().getPort());
            var base = SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("orders", "Orders", yaml)), List.of());
            configurations.save(new SystemAssistantConfiguration(base.apis(), base.skills(), List.of(new SystemAssistantConfiguration.Profile("test_profile", "Test", "*", List.of(new SystemAssistantConfiguration.ApiBinding("orders", "crm_bearer")), List.of())), List.of(
                    new pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile("crm_bearer", "CRM test", "BEARER", null, null,
                            "public-crm-token", null, null, null, null, null, null, null))));
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var tool = client.listTools().tools().stream().filter(item -> item.description().contains("operationId: " + operationId)).findFirst().orElseThrow();
                assertThat(tool.name()).hasSize(64).startsWith("oa_orders_");
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var properties = mapper.valueToTree(tool.inputSchema()).path("properties").path("body").path("properties");
                assertThat(properties.path("note").path("type").toString()).isEqualTo("[\"string\",\"null\"]");
                assertThat(properties.path("customerEmail").path("format").asText()).isEqualTo("email");
                var body = new HashMap<String, Object>(); body.put("customerEmail", "tester@example.com"); body.put("note", null);
                body.put("items", List.of(Map.of("productId", "p1", "quantity", 2)));
                var result = client.callTool(new McpSchema.CallToolRequest(tool.name(), Map.of("body", body)));
                assertThat(result.isError()).isFalse(); assertThat(result.content().toString()).contains("NEW");
                assertThat(mapper.readTree(received.get()).get("note").isNull()).isTrue();
                assertThat(authorization.get()).isEqualTo("Bearer public-crm-token");
            }
        } finally { api.stop(0); configurations.save(SystemAssistantConfiguration.empty()); }
    }

    @Test
    void transportsCompositionAndKeepsUncertainWriteStateAcrossMcpRequests() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/items/1", exchange -> {
            calls.incrementAndGet(); var bytes = "{\"id\":123}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.createContext("/collections", exchange -> {
            calls.incrementAndGet(); var bytes = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var source = Files.readString(Path.of("src/test/resources/openapi/composition-api.yaml"))
                    .replace(":8099", ":" + api.getAddress().getPort());
            configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("products", "Products", source)), List.of()));
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var tool = client.listTools().tools().stream().filter(item -> item.name().equals("oa_products_createItem")).findFirst().orElseThrow();
                var schema = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(tool.inputSchema());
                assertThat(schema.path("properties").path("body").path("oneOf")).hasSize(2);
                assertThat(schema.toString()).contains("allOf", "Discriminator").doesNotContain("generatedId");
                var arguments = Map.<String, Object>of("path", Map.of("batchId", 1), "query", Map.of("ownerKey", "test"), "body", Map.of("type", "BASIC", "size", 1));
                assertThat(client.callTool(new McpSchema.CallToolRequest(tool.name(), arguments)).isError()).isFalse();
                var invalid = Map.<String, Object>of("path", Map.of("batchId", 1), "query", Map.of("ownerKey", "test"), "body", Map.of("type", "BASIC", "size", 0));
                assertThat(client.callTool(new McpSchema.CallToolRequest(tool.name(), invalid)).isError()).isTrue();
                var uncertain = client.callTool(new McpSchema.CallToolRequest("oa_products_getOrCreateCollection", Map.of("query", Map.of("ownerKey", "test"))));
                assertThat(uncertain.content().toString()).contains("OUTCOME_UNKNOWN");
                assertThat(client.callTool(new McpSchema.CallToolRequest(tool.name(), arguments)).content().toString()).contains("WRITE_BLOCKED");
                assertThat(calls).hasValue(2);
            }
        } finally { api.stop(0); configurations.save(SystemAssistantConfiguration.empty()); }
    }

    @Test
    void executesPatchAndPreservesItsWriteGuardAcrossMcpCalls() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var method = new java.util.concurrent.atomic.AtomicReference<String>();
        var correlation = new java.util.concurrent.atomic.AtomicReference<String>();
        var body = new java.util.concurrent.atomic.AtomicReference<String>();
        var uncertain = new java.util.concurrent.atomic.AtomicBoolean();
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/items/7", exchange -> {
            calls.incrementAndGet(); method.set(exchange.getRequestMethod());
            correlation.set(exchange.getRequestHeaders().getFirst("correlationId"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = (uncertain.get() ? "not-json" : "{\"updated\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var source = Files.readString(Path.of("src/test/resources/openapi/patch-api.yaml")).replace("\r\n", "\n")
                    .replace("parameters:\n      - {name: id", "parameters:\n      - {name: correlationId, in: header}\n      - {name: id")
                    .replace(":8099", ":" + api.getAddress().getPort());
            var selected = selections.select("items", source, List.of(new pl.mkn.tdw.integrations.openapi.contract.OpenApiSelection.OperationKey("PATCH", "/items/{id}")));
            configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("items", "Items", selected.yaml())), List.of()));
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var listed = client.listTools().tools();
                assertThat(listed).extracting(McpSchema.Tool::name).containsExactly("oa_items_patchItem");
                assertThat(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(listed.get(0).inputSchema()).toString()).doesNotContain("correlationId", "headers");
                var arguments = Map.<String, Object>of("path", Map.of("id", 7), "body", Map.of("note", "Changed"));
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_items_patchItem", arguments)).isError()).isFalse();
                assertThat(method).hasValue("PATCH"); assertThat(body).hasValue("{\"note\":\"Changed\"}");
                assertThat(correlation.get()).isNull();
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_items_patchItem", Map.of("path", Map.of("id", 7), "body", Map.of()))).isError()).isTrue();
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_items_patchItem", Map.of("path", Map.of("id", 7), "body", Map.of("note", "Changed"), "headers", Map.of("correlationId", "model-value")))).isError()).isTrue();
                assertThat(calls).hasValue(1);
                uncertain.set(true);
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_items_patchItem", arguments)).content().toString()).contains("OUTCOME_UNKNOWN");
                assertThat(client.callTool(new McpSchema.CallToolRequest("oa_items_patchItem", arguments)).content().toString()).contains("WRITE_BLOCKED");
                assertThat(calls).hasValue(2);
            }
        } finally { api.stop(0); configurations.save(SystemAssistantConfiguration.empty()); }
    }

    @TempDir static Path workspace;
    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) {
        registry.add("tdw.workspace.directory", () -> workspace.toString());
    }
    @LocalServerPort int port;
    @Autowired SystemAssistantConfigurationService configurations;
    @Autowired pl.mkn.tdw.integrations.openapi.OpenApiSelectionPort selections;

    @Test
    void listsCallsAndReloadsOnlyUserToolsOverRealMcpTransport() throws Exception {
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/tickets", exchange -> {
            var bytes = "{\"title\":\"Fikcyjne zgłoszenie\",\"status\":\"OPEN\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.createContext("/changed", exchange -> {
            var bytes = "{\"source\":\"changed-handler\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var yaml = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"))
                    .replace(":8099", ":" + api.getAddress().getPort());
            configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "Demo", yaml)), List.of()));
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                    .endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var tools = client.listTools().tools();
                assertThat(tools).hasSize(3).allSatisfy(tool -> assertThat(tool.name()).startsWith("oa_crm_"));
                var name = tools.stream().filter(tool -> tool.name().endsWith("getTicket")).findFirst().orElseThrow().name();
                var result = client.callTool(new McpSchema.CallToolRequest(name, Map.of("path", Map.of("id", "CRM-101"))));
                assertThat(result.isError()).isFalse();
                assertThat(result.content().toString()).contains("OPEN");
                configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "Demo", yaml.replace("Odczytaj fikcyjne zgłoszenie", "NOWY OPIS")
                                .replace("/tickets/{id}", "/changed/{id}")
                                .replace("schema: {type: string}", "schema: {type: string, enum: [CRM-202]}"))), List.of()));
                var changed = client.listTools().tools();
                assertThat(changed).hasSize(3);
                var changedRead = changed.stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
                assertThat(changedRead.description()).contains("NOWY OPIS");
                assertThat(changedRead.inputSchema().toString()).contains("CRM-202");
                assertThat(client.callTool(new McpSchema.CallToolRequest(name, Map.of("path", Map.of("id", "CRM-101")))).isError()).isTrue();
                var changedResult = client.callTool(new McpSchema.CallToolRequest(name, Map.of("path", Map.of("id", "CRM-202"))));
                assertThat(changedResult.isError()).isFalse();
                assertThat(changedResult.content().toString()).contains("changed-handler");
                var subset = selections.select("crm", yaml, List.of(new pl.mkn.tdw.integrations.openapi.contract.OpenApiSelection.OperationKey("GET", "/tickets/{id}")));
                configurations.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "Demo", subset.yaml())), List.of()));
                assertThat(client.listTools().tools()).extracting(tool -> tool.name()).containsExactly("oa_crm_getTicket");
                assertThatThrownBy(() -> client.callTool(new McpSchema.CallToolRequest("oa_crm_updateTicket", Map.of())))
                        .isInstanceOf(RuntimeException.class);
                configurations.save(SystemAssistantTestSupport.config(List.of(), List.of()));
                assertThat(client.listTools().tools()).isEmpty();
                assertThatThrownBy(() -> client.callTool(new McpSchema.CallToolRequest(name, Map.of("path", Map.of("id", "CRM-101")))))
                        .isInstanceOf(RuntimeException.class);
            }
        } finally { api.stop(0); }
    }

    @Test
    void isolatesAuthenticationAndSessionsForTwoProfilesOfTheSameApi() throws Exception {
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/who", exchange -> {
            var auth = exchange.getRequestHeaders().getFirst("Authorization");
            var bytes = ("{\"auth\":\"" + (auth == null ? "anonymous" : auth) + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        api.start();
        try {
            var yaml = """
                    openapi: 3.0.3
                    servers: [{url: http://127.0.0.1:%d}]
                    paths:
                      /who:
                        get:
                          operationId: who
                          responses: {'200': {description: CRM user}}
                    """.formatted(api.getAddress().getPort());
            var profiles = java.util.stream.Stream.of("operator", "admin", "anonymous").map(id ->
                    new SystemAssistantConfiguration.Profile(id, id, "*", List.of(new SystemAssistantConfiguration.ApiBinding("crm",
                            id.equals("anonymous") ? null : id)), List.of())).toList();
            var auth = java.util.stream.Stream.of("operator", "admin").map(id ->
                    new pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile(id, id, "BEARER", null, null,
                            "public-" + id, null, null, null, null, null, null, null)).toList();
            configurations.save(new SystemAssistantConfiguration(List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "CRM", yaml)), List.of(), profiles, auth));
            var root = "http://127.0.0.1:" + port;
            try (var first = McpClient.sync(HttpClientStreamableHttpTransport.builder(root).endpoint(OpenApiMcpEndpoint.path("operator")).build()).build();
                 var second = McpClient.sync(HttpClientStreamableHttpTransport.builder(root).endpoint(OpenApiMcpEndpoint.path("admin")).build()).build();
                 var anonymous = McpClient.sync(HttpClientStreamableHttpTransport.builder(root).endpoint(OpenApiMcpEndpoint.path("anonymous")).build()).build()) {
                first.initialize(); second.initialize(); anonymous.initialize();
                assertThat(first.listTools().tools()).extracting(tool -> tool.name()).containsExactly("oa_crm_who");
                assertThat(second.listTools().tools()).extracting(tool -> tool.name()).containsExactly("oa_crm_who");
                assertThat(first.callTool(new McpSchema.CallToolRequest("oa_crm_who", Map.of())).content().toString()).contains("public-operator").doesNotContain("public-admin");
                assertThat(second.callTool(new McpSchema.CallToolRequest("oa_crm_who", Map.of())).content().toString()).contains("public-admin").doesNotContain("public-operator");
                assertThat(anonymous.callTool(new McpSchema.CallToolRequest("oa_crm_who", Map.of())).content().toString()).contains("anonymous");
            }
            var http = java.net.http.HttpClient.newHttpClient();
            var init = java.net.http.HttpRequest.newBuilder(java.net.URI.create(root + OpenApiMcpEndpoint.path("operator")))
                    .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"crm-test\",\"version\":\"1\"}}}"))
                    .build();
            var initialized = http.send(init, java.net.http.HttpResponse.BodyHandlers.ofString());
            var session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
            var wrongScope = java.net.http.HttpRequest.newBuilder(java.net.URI.create(root + OpenApiMcpEndpoint.path("admin")))
                    .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Mcp-Session-Id", session)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}" )).build();
            assertThat(http.send(wrongScope, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            for (var path : List.of("/api/openapi-tools/mcp", OpenApiMcpEndpoint.path("missing"))) {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(root + path)).POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json").build();
                assertThat(http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            }
        } finally { api.stop(0); configurations.save(SystemAssistantConfiguration.empty()); }
    }
}
