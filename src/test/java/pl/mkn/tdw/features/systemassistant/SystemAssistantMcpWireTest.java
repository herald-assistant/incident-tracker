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
    void publishesNullableArgumentsAndCallsAnApiThroughLocalhost() throws Exception {
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
                    .replace(":8081", ":" + api.getAddress().getPort());
            var base = SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("orders", "Orders", yaml)), List.of());
            configurations.save(new SystemAssistantConfiguration(base.apis(), base.skills(), List.of(new SystemAssistantConfiguration.Profile("test_profile", "Test", "*", List.of(new SystemAssistantConfiguration.ApiBinding("orders", "crm_bearer")), List.of())), List.of(
                    new pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile("crm_bearer", "CRM test", "BEARER", null, null,
                            "public-crm-token", null, null, null, null, null, null, null))));
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(OpenApiMcpEndpoint.path("test_profile")).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                var tool = client.listTools().tools().stream().filter(item -> item.name().equals("oa_orders_createOrder")).findFirst().orElseThrow();
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
