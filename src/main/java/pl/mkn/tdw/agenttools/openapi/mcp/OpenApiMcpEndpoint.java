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
import pl.mkn.tdw.integrations.openapi.contract.OpenApiExecution;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Each operator-selected scope has an isolated MCP transport and sessions. */
public class OpenApiMcpEndpoint implements AutoCloseable {
    private static final String PREFIX = "/api/openapi-tools/";
    private final Map<String, Scope> scopes = new LinkedHashMap<>();
    private final OpenApiToolCatalog catalog;
    private final OpenApiToolSetFactory callbacks;
    private final ObjectMapper mapper;

    public static String path(String scopeId) {
        if (scopeId == null || !scopeId.matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalArgumentException("Nieprawidłowy scope MCP.");
        return PREFIX + scopeId + "/mcp";
    }

    public OpenApiMcpEndpoint(OpenApiToolCatalog catalog, OpenApiToolSetFactory callbacks, ObjectMapper mapper) {
        this.catalog = catalog; this.callbacks = callbacks; this.mapper = mapper;
        catalog.attach(this::reconcile);
    }

    public RouterFunction<ServerResponse> routes() {
        return request -> {
            var path = request.path();
            if (!path.startsWith(PREFIX) || !path.endsWith("/mcp") || path.length() <= PREFIX.length() + 4) return Optional.empty();
            var id = path.substring(PREFIX.length(), path.length() - "/mcp".length());
            Scope scope;
            synchronized (this) { scope = catalog.current().containsKey(id) ? scopes.get(id) : null; }
            return scope == null ? Optional.empty() : scope.transport.getRouterFunction().route(request);
        };
    }

    private synchronized void reconcile(Map<String, List<OpenApiExecution>> next) {
        next.forEach((id, executions) -> scopes.computeIfAbsent(id, Scope::new).reconcile(executions));
        for (var id : new ArrayList<>(scopes.keySet())) if (!next.containsKey(id)) scopes.remove(id).server.close();
    }

    private class Scope {
        private final String id;
        private final WebMvcStreamableServerTransportProvider transport;
        private final McpSyncServer server;
        private final Set<String> registered = new LinkedHashSet<>();
        private final Map<String, AtomicBoolean> sessionGuards = new HashMap<>();

        Scope(String id) {
            this.id = id;
            var json = new JacksonMcpJsonMapper(mapper);
            transport = WebMvcStreamableServerTransportProvider.builder().jsonMapper(json).mcpEndpoint(path(id)).build();
            server = McpServer.sync(transport).jsonMapper(json).serverInfo("tdw-openapi-local", "1")
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build()).build();
        }

        void reconcile(List<OpenApiExecution> executions) {
            for (var name : new ArrayList<>(registered)) { server.removeTool(name); registered.remove(name); }
            for (var execution : executions) {
                var operation = execution.operation();
                var tool = McpSchema.Tool.builder().name(operation.name()).description(operation.description())
                        .inputSchema(new JacksonMcpJsonMapper(mapper), operation.inputSchema().toString()).build();
                server.addTool(McpServerFeatures.SyncToolSpecification.builder().tool(tool)
                        .callHandler((exchange, request) -> call(exchange.sessionId(), operation.name(), request.arguments())).build());
                registered.add(operation.name());
            }
        }

        private synchronized AtomicBoolean guard(String sessionId) {
            var current = sessionGuards.get(sessionId);
            if (current != null) return current;
            if (sessionGuards.size() >= 1024) throw new IllegalArgumentException("Osiągnięto limit sesji MCP tego profilu. Utwórz ponownie ekspozycję profilu.");
            var next = new AtomicBoolean(); sessionGuards.put(sessionId, next); return next;
        }

        private McpSchema.CallToolResult call(String sessionId, String name, Map<String, Object> arguments) {
            try {
                var execution = catalog.current().getOrDefault(id, List.of()).stream()
                        .filter(item -> item.operation().name().equals(name)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Operacja nie jest już dostępna w wybranym profilu."));
                var guard = guard(sessionId);
                String raw;
                synchronized (guard) {
                    raw = callbacks.create(List.of(execution), guard).get(0).call(mapper.writeValueAsString(arguments));
                }
                var result = mapper.readTree(raw);
                var failed = !result.path("outcome").asText().equals("RESPONSE_RECEIVED") || result.path("statusCode").asInt(500) >= 300;
                return McpSchema.CallToolResult.builder().addTextContent(raw).isError(failed).build();
            } catch (Exception exception) {
                return McpSchema.CallToolResult.builder().addTextContent(exception instanceof pl.mkn.tdw.integrations.openapi.contract.OpenApiException
                        || exception instanceof IllegalArgumentException ? exception.getMessage() : "Nie udało się wykonać operacji OpenAPI.").isError(true).build();
            }
        }
    }

    @Override public synchronized void close() { scopes.values().forEach(scope -> scope.server.close()); scopes.clear(); }
}
