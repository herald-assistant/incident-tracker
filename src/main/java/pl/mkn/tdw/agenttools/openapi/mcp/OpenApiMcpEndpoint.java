package pl.mkn.tdw.agenttools.openapi.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;

import java.util.*;

/**
 * Dedicated server kept out of the application's global MCP bean/catalog.
 */
public class OpenApiMcpEndpoint implements AutoCloseable {
    public static final String PATH = "/api/openapi-tools/mcp";
    private final WebMvcStreamableServerTransportProvider transport;
    private final McpSyncServer server;
    private final OpenApiToolCatalog catalog;
    private final OpenApiToolSetFactory callbacks;
    private final ObjectMapper mapper;
    private final Set<String> registered = new LinkedHashSet<>();

    public OpenApiMcpEndpoint(OpenApiToolCatalog catalog, OpenApiToolSetFactory callbacks, ObjectMapper mapper) {
        this.catalog = catalog;
        this.callbacks = callbacks;
        this.mapper = mapper;
        var jsonMapper = new JacksonMcpJsonMapper(mapper);
        transport = WebMvcStreamableServerTransportProvider.builder().jsonMapper(jsonMapper).mcpEndpoint(PATH).build();
        server = McpServer.sync(transport).jsonMapper(jsonMapper).serverInfo("tdw-openapi-local", "1")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build()).build();
        catalog.attach(this::reconcile);
    }

    public RouterFunction<ServerResponse> routes() {
        return transport.getRouterFunction();
    }

    private synchronized void reconcile(List<OpenApiOperation> operations) {
        for (var name : new ArrayList<>(registered)) {
            server.removeTool(name);
            registered.remove(name);
        }
        for (var operation : operations) {
            var tool = McpSchema.Tool.builder().name(operation.name()).description(operation.description())
                    .inputSchema(new JacksonMcpJsonMapper(mapper), operation.inputSchema().toString()).build();
            server.addTool(McpServerFeatures.SyncToolSpecification.builder().tool(tool)
                    .callHandler((exchange, request) -> call(operation.name(), request.arguments())).build());
            registered.add(operation.name());
        }
    }

    private McpSchema.CallToolResult call(String name, Map<String, Object> arguments) {
        try {
            var operation = catalog.current().stream().filter(op -> op.name().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Operacja nie jest już dostępna."));
            var raw = callbacks.create(List.of(operation)).get(0).call(mapper.writeValueAsString(arguments));
            var result = mapper.readTree(raw);
            var failed = !result.path("outcome").asText().equals("RESPONSE_RECEIVED") || result.path("statusCode").asInt(500) >= 300;
            return McpSchema.CallToolResult.builder().addTextContent(raw).isError(failed).build();
        } catch (Exception exception) {
            return McpSchema.CallToolResult.builder().addTextContent(exception instanceof pl.mkn.tdw.integrations.openapi.contract.OpenApiException
                    || exception instanceof IllegalArgumentException ? exception.getMessage() : "Nie udało się wykonać operacji OpenAPI.").isError(true).build();
        }
    }

    @Override
    public void close() {
        server.close();
    }
}
