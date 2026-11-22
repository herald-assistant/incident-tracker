package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;
import pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationService;
import pl.mkn.tdw.features.systemassistant.config.*;
import pl.mkn.tdw.features.systemassistant.contract.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Explicit opt-in: real Copilot, exclusively fictional CRM data and loopback HTTP. */
@EnabledIfEnvironmentVariable(named = "SYSTEM_ASSISTANT_LIVE_TEST", matches = "true")
@SpringBootTest(properties = { "system-assistant.enabled=true", "server.address=127.0.0.1",
        "analysis.ai.copilot.telemetry.enabled=false", "analysis.ai.copilot.send-and-wait-timeout=2m" })
@DirtiesContext
class SystemAssistantCopilotLiveTest {
    @TempDir static Path runtime;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("analysis.ai.copilot.copilot-home", () -> runtime.resolve("copilot").toString());
        registry.add("analysis.ai.copilot.working-directory", () -> runtime.resolve("work").toString());
    }
    // Prevents pilot configuration from touching the operator's actual configuration.
    @MockitoBean SystemAssistantConfigurationStore store;
    @Autowired SystemAssistantConversationService chats;
    @Autowired SystemAssistantConfigurationService configurations;
    @Autowired CopilotModelOptionsProvider models;
    @Autowired ObjectMapper mapper;

    @Test
    void completesCrmTaskAndUsesEditedToolsAndSkillsInSameChat() throws Exception {
        var state = new AtomicReference<>(mapper.readTree("{\"title\":\"Fikcyjne zgłoszenie\",\"status\":\"OPEN\"}"));
        var writes = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tickets", exchange -> {
            if (exchange.getRequestMethod().equals("PUT")) { state.set(mapper.readTree(exchange.getRequestBody())); writes.incrementAndGet(); }
            var bytes = mapper.writeValueAsBytes(state.get());
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/simulate", exchange -> {
            var input = mapper.readTree(exchange.getRequestBody());
            var bytes = mapper.writeValueAsBytes(Map.of("total", input.get("amount").decimalValue().multiply(input.get("months").decimalValue()), "saved", false));
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/contacts", exchange -> {
            var bytes = mapper.writeValueAsBytes(Map.of("id", "CRM-201", "name", "Kontakt demonstracyjny"));
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        String id = null;
        try {
            var yaml = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml")).replace(":8099", ":" + server.getAddress().getPort());
            var contacts = Files.readString(Path.of("src/test/resources/openapi/contacts-api.yaml")).replace(":8099", ":" + server.getAddress().getPort());
            var catalog = models.modelOptions(CopilotRunAuth.localToken());
            var selected = System.getenv("COPILOT_TEST_MODEL");
            var model = selected != null ? selected : !catalog.defaultModel().isBlank() ? catalog.defaultModel() : catalog.models().get(0).id();
            configurations.save(config(yaml, null, "Odpowiadaj krótko po polsku."));
            id = chats.create(null).id();
            var read = turn(id, "Odczytaj zgłoszenie CRM-101 narzędziem getTicket i podaj aktualny status.", model);
            assertThat(read.content()).contains("OPEN"); assertThat(read.toolEvidenceSections()).isNotEmpty();
            var simulation = turn(id, "Użyj simulateCost dla amount=120 i months=12. Nic nie zapisuj, podaj total.", model);
            assertThat(simulation.content().replace(" ", "").replace("\u00a0", "")).contains("1440");
            turn(id, "Zmień status CRM-101 na IN_PROGRESS. Zachowaj tytuł. Wykonaj jeden PUT, następnie potwierdź stan GET.", model);
            assertThat(writes).hasValue(1); assertThat(state.get().path("status").asText()).isEqualTo("IN_PROGRESS");
            configurations.save(config(yaml, contacts, "Każdą odpowiedź zacznij dokładnie od NOWE_INSTRUKCJE."));
            var edited = turn(id, "Odczytaj kontakt CRM-201 z aktualnego narzędzia getContact.", model);
            assertThat(edited.content()).contains("NOWE_INSTRUKCJE", "CRM-201");
            configurations.save(config(yaml.replace("Odczytaj fikcyjne zgłoszenie CRM", "Odczyt po edycji konfiguracji"), null,
                    "Każdą odpowiedź zacznij dokładnie od OSTATNIE_INSTRUKCJE."));
            var updated = turn(id, "Odczytaj aktualny status zgłoszenia CRM-101. Nie wykonuj żadnych zapisów.", model);
            assertThat(updated.content()).contains("OSTATNIE_INSTRUKCJE", "IN_PROGRESS"); assertThat(writes).hasValue(1);
            configurations.save(SystemAssistantConfiguration.empty());
            var empty = turn(id, "Czy masz teraz dostępne narzędzia? Odpowiedz jednym zdaniem. Nie powtarzaj wcześniejszych działań.", model);
            assertThat(empty.toolEvidenceSections()).isEmpty(); assertThat(empty.usage()).isNotNull();
            assertThat(empty.content().toLowerCase(java.util.Locale.ROOT)).containsAnyOf("nie mam", "brak", "nie ma", "nie są", "nie posiadam");
            assertThat(writes).hasValue(1);
        } finally { if (id != null && !chats.get(id).busy()) chats.remove(id); server.stop(0); }
    }
    private SystemAssistantConfiguration config(String yaml, String contacts, String skill) {
        var apis = new ArrayList<SystemAssistantConfiguration.ApiDefinition>();
        if (yaml != null) apis.add(new SystemAssistantConfiguration.ApiDefinition("crm", "Demo", yaml));
        if (contacts != null) apis.add(new SystemAssistantConfiguration.ApiDefinition("contacts", "Kontakty", contacts));
        return SystemAssistantTestSupport.config(apis, List.of(new SystemAssistantConfiguration.Skill("demo", "Demo", skill)));
    }
    private pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse turn(String id, String text, String model) {
        chats.send(id, new SystemAssistantMessageRequest(text, "test_profile", model, null));
        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofMillis(250)).until(() -> !chats.get(id).busy());
        var result = chats.get(id).messages().get(chats.get(id).messages().size() - 1);
        assertThat(result.status()).as(result.errorCode() + ": " + result.errorMessage()).isEqualTo("COMPLETED");
        assertThat(result.prompt()).isNull();
        return result;
    }
}
