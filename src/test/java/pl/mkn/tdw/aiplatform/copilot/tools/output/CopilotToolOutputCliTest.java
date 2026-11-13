package pl.mkn.tdw.aiplatform.copilot.tools.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.ProviderConfig;
import com.github.copilot.rpc.ToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotCliExecutableResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TDW_COPILOT_OUTPUT_CLI_TEST", matches = "(?i)true")
class CopilotToolOutputCliTest {

    private static final String SOURCE = "crm_get_contact_result";
    private static final String READER = CopilotToolOutputReader.TOOL_NAME;
    private static final String MARKER = "CRM_END_CONTACTS_82F5";
    private static final String PAYLOAD = "Fikcyjna karta kontaktu CRM. ".repeat(2_800) + MARKER;
    private static final Pattern SPILL_PATH = Pattern.compile("(?m)^.*Saved to:[ \\t]*([^\\r\\n]+)");

    @TempDir
    Path workspace;

    @Test
    void readsNativeSpillInCreateAndRegeneratesItAfterResume() throws Exception {
        var mapper = new ObjectMapper();
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory(workspace.toString());
        properties.setCopilotHome(workspace.resolve("crm-copilot").toString());
        var resolver = new CopilotCliExecutableResolver();
        var reader = new CopilotToolOutputReader(properties, mapper);
        var factory = new CopilotSessionConfigFactory(properties, null, null, resolver, reader);
        var sourceCalls = new AtomicInteger();
        var source = ToolDefinition.createSkipPermission(SOURCE, "Read fictional CRM contact records.",
                Map.of("type", "object", "properties", Map.of()), invocation -> {
                    return CompletableFuture.completedFuture(sourceCalls.incrementAndGet() < 3
                            ? PAYLOAD : Map.of("contacts", List.of(Map.of("status", "active")), "notes", PAYLOAD));
                });
        var request = new CopilotSessionConfigRequest("crm-large-output-cli", List.of(source), List.of(SOURCE),
                new CopilotModelSelection("gpt-4o", null), null, false);
        var peer = new ScriptedModel(mapper, properties.resolvedToolOutputDirectory());
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", peer::respond);
        server.start();
        try {
            var provider = new ProviderConfig().setType("openai").setWireApi("completions")
                    .setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1").setApiKey("fictional-crm");
            var create = factory.sessionConfig(request).setProvider(provider);
            var resume = factory.resumeSessionConfig(request).setProvider(provider);
            try (var client = new CopilotClient(clientOptions(properties, resolver))) {
                client.start().get(30, TimeUnit.SECONDS);
                var status = client.getStatus().get(10, TimeUnit.SECONDS);
                System.out.printf("Copilot CLI %s protocol %s%n", status.getVersion(), status.getProtocolVersion());
                try (var session = client.createSession(create).get(30, TimeUnit.SECONDS)) {
                    var result = session.sendAndWait(new MessageOptions().setPrompt("Read the fictional CRM contacts."), 90_000L)
                            .get(95, TimeUnit.SECONDS);
                    assertThat(result.getData().content()).isEqualTo(MARKER);
                }
                assertThat(peer.spill).doesNotExist();
                peer.resume = true;
                try (var session = client.resumeSession(request.sessionId(), resume).get(30, TimeUnit.SECONDS)) {
                    var result = session.sendAndWait(new MessageOptions().setPrompt("Continue reading the fictional CRM contacts."), 90_000L)
                            .get(95, TimeUnit.SECONDS);
                    assertThat(result.getData().content()).isEqualTo(MARKER);
                }
                assertThat(peer.unavailable).isTrue();
                assertThat(sourceCalls).hasValue(2);
                assertThat(peer.chunks).isGreaterThan(16);
                assertThat(peer.maxBytes).isLessThanOrEqualTo(8_192);
                System.out.printf("CRM spill %d UTF-8 bytes, %d pages, largest serialized page %d bytes; resumed read recovered%n",
                        PAYLOAD.getBytes(StandardCharsets.UTF_8).length, peer.chunks, peer.maxBytes);
                peer.json = true;
                try (var session = client.resumeSession(request.sessionId(), resume).get(30, TimeUnit.SECONDS)) {
                    var result = session.sendAndWait(new MessageOptions().setPrompt("Read the fictional CRM contact status."), 90_000L)
                            .get(95, TimeUnit.SECONDS);
                    assertThat(result.getData().content()).isEqualTo("active");
                }
                assertThat(sourceCalls).hasValue(3);
                client.stop().get(20, TimeUnit.SECONDS);
            }
        } finally {
            server.stop(0);
        }
        assertThat(peer.failure.get()).isNull();
    }

    private CopilotClientOptions clientOptions(CopilotSdkProperties properties, CopilotCliExecutableResolver resolver) {
        var environment = new HashMap<>(System.getenv());
        environment.keySet().removeIf(name -> name.toUpperCase(Locale.ROOT).matches(".*(TOKEN|API_KEY|PROXY).*"));
        environment.put("COPILOT_AUTO_UPDATE", "false");
        var cache = workspace.resolve("cli-cache").toString();
        environment.putIfAbsent("COPILOT_PKG_CACHE_HOME", cache);
        environment.putIfAbsent("COPILOT_CACHE_HOME", cache);
        var cli = System.getenv().getOrDefault("COPILOT_CLI_PATH", "copilot");
        return new CopilotClientOptions().setCliPath(resolver.resolve(cli, workspace.toString()))
                .setUseLoggedInUser(false).setEnvironment(environment).setCwd(workspace.toString())
                .setCopilotHome(properties.resolvedCopilotHome().toString());
    }

    private static final class ScriptedModel {
        private final ObjectMapper mapper;
        private final Path directory;
        private final StringBuilder reconstructed = new StringBuilder();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private int calls;
        private int chunks;
        private int maxBytes;
        private volatile boolean resume;
        private volatile boolean json;
        private boolean startedResume;
        private boolean startedJson;
        private boolean unavailable;
        private volatile Path spill;

        private ScriptedModel(ObjectMapper mapper, Path directory) {
            this.mapper = mapper;
            this.directory = directory;
        }

        private void respond(HttpExchange exchange) throws IOException {
            try (exchange) {
                try {
                    var input = mapper.readTree(exchange.getRequestBody());
                    var message = next(input.path("messages"));
                    var reply = mapper.writeValueAsBytes(Map.of("id", "crm-completion-" + calls,
                            "object", "chat.completion", "created", 123, "model", "gpt-4o",
                            "choices", List.of(Map.of("index", 0, "message", message,
                                    "finish_reason", message.containsKey("tool_calls") ? "tool_calls" : "stop")),
                            "usage", Map.of("prompt_tokens", 100, "completion_tokens", 10, "total_tokens", 110)));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, reply.length);
                    exchange.getResponseBody().write(reply);
                } catch (Exception | AssertionError exception) {
                    failure.compareAndSet(null, exception);
                    exchange.sendResponseHeaders(400, -1);
                }
            }
        }

        private Map<String, Object> next(JsonNode messages) throws IOException {
            calls++;
            if (calls == 1) {
                return call(SOURCE, Map.of());
            }
            if (json && !startedJson) {
                startedJson = true;
                return call(SOURCE, Map.of());
            }
            if (resume && !startedResume) {
                startedResume = true;
                return call(READER, Map.of("path", spill.toString()));
            }
            var last = messages.get(messages.size() - 1);
            assertThat(last.path("role").asText()).isEqualTo("tool");
            var text = last.path("content").asText();
            var saved = SPILL_PATH.matcher(text);
            if (saved.find()) {
                assertThat(text).doesNotContain(MARKER);
                var nextSpill = Path.of(saved.group(1).strip());
                assertThat(nextSpill).startsWith(directory).exists();
                if (resume) {
                    assertThat(nextSpill).isNotEqualTo(spill);
                }
                spill = nextSpill;
                reconstructed.setLength(0);
                return call(READER, json ? Map.of("path", spill.toString(), "jsonPointer", "/contacts/0/status")
                        : Map.of("path", spill.toString()));
            }

            maxBytes = Math.max(maxBytes, text.getBytes(StandardCharsets.UTF_8).length);
            assertThat(maxBytes).isLessThanOrEqualTo(8_192);
            var result = mapper.readTree(text);
            if ("OUTPUT_UNAVAILABLE".equals(result.path("status").asText())) {
                assertThat(resume && !unavailable).isTrue();
                unavailable = true;
                return call(SOURCE, Map.of());
            }
            assertThat(result.path("status").asText()).isEqualTo("OK");
            if (json) {
                return Map.of("role", "assistant", "content", result.path("value").asText());
            }
            chunks++;
            reconstructed.append(result.path("content").asText());
            if (result.has("nextCursor")) {
                return call(READER, Map.of("path", spill.toString(), "cursor", result.path("nextCursor").asText()));
            }
            assertThat(reconstructed.toString()).isEqualTo(PAYLOAD);
            return Map.of("role", "assistant", "content", reconstructed.substring(reconstructed.length() - MARKER.length()));
        }

        private Map<String, Object> call(String name, Map<String, ?> arguments) throws IOException {
            return Map.of("role", "assistant", "tool_calls", List.of(Map.of("id", "call_crm_" + calls, "type", "function",
                    "function", Map.of("name", name, "arguments", mapper.writeValueAsString(arguments)))));
        }
    }
}
