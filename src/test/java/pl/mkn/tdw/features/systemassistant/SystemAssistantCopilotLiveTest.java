package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
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
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    void readsFilesReturnedByApiWithoutSavingAttachments() throws Exception {
        var pdfReads = new AtomicInteger();
        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/file", exchange -> {
            try {
                var format = exchange.getRequestURI().getPath().substring("/file/".length());
                byte[] bytes;
                String mime;
                if (format.equals("pdf")) {
                    bytes = pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures.pdf("PDF-" + (830 + pdfReads.incrementAndGet()));
                    mime = "application/pdf";
                } else if (format.equals("png")) {
                    bytes = pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures.png("IMAGE-627"); mime = "image/png";
                } else {
                    bytes = pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures.docx("WORD-682");
                    mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { exchange.close(); }
        });
        api.start();
        String id = null;
        try {
            configurations.save(config(pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures.yaml(
                    "http://127.0.0.1:" + api.getAddress().getPort()), null, "Odpowiadaj krótko po polsku."));
            id = chats.create(null).id();
            var model = Optional.ofNullable(System.getenv("COPILOT_TEST_MODEL")).orElse("gpt-6.1-terra");
            assertThat(turn(id, "Wywołaj getFile raz z format=pdf. Odczytaj FILE-CODE z otrzymanego pliku.", model).content())
                    .contains("PDF-831");
            assertThat(turn(id, "Ponownie wywołaj getFile raz z format=pdf. Odczytaj aktualny FILE-CODE, nie korzystaj z poprzedniego wyniku.", model).content())
                    .contains("PDF-832");
            assertThat(pdfReads).hasValue(2);
            assertThat(turn(id, "Wywołaj getFile z format=png i odczytaj FILE-CODE z obrazu.", model).content()).contains("IMAGE-627");
            assertThat(turn(id, "Wywołaj getFile z format=docx. Odczytaj FILE-CODE z tekstu oraz osobno z osadzonego obrazu.", model).content())
                    .contains("WORD-682", "EMBEDDED-831");
            assertThat(chats.get(id).attachments()).isEmpty();
            assertThat(mapper.writeValueAsString(chats.get(id))).doesNotContain("iVBORw0KGgo", "/9j/4AAQSk", "binaryResultsForLlm");
        } finally {
            if (id != null && !chats.get(id).busy()) chats.remove(id);
            api.stop(0);
        }
    }

    @Test
    void readsScannedPdfAndUsesOpenApiAcrossNewSessions() throws Exception {
        var reads = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tickets", exchange -> {
            reads.incrementAndGet();
            var bytes = mapper.writeValueAsBytes(Map.of("id", "CRM-101", "status", "OPEN"));
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        String id = null;
        try {
            var yaml = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"))
                    .replace(":8099", ":" + server.getAddress().getPort());
            configurations.save(config(yaml, null, "Odpowiadaj krótko po polsku."));
            id = chats.create(null).id();
            chats.attach(id, "scan.pdf", new ByteArrayInputStream(scannedPdf()));
            var model = Optional.ofNullable(System.getenv("COPILOT_TEST_MODEL")).orElse("gpt-5.6-luna");
            var first = turn(id, "Odczytaj oba DOCUMENT-CODE ze stron załączonego PDF. "
                    + "Następnie użyj getTicket dla CRM-101 i podaj status.", model);
            assertThat(first.content()).contains("ALPHA-726", "BETA-918", "OPEN");
            assertThat(first.toolEvidenceSections()).isNotEmpty();
            var second = turn(id, "Ponownie odczytaj oba DOCUMENT-CODE z załączonego PDF i użyj "
                    + "getTicket dla CRM-101, podając aktualny status.", model);
            assertThat(second.content()).contains("ALPHA-726", "BETA-918", "OPEN");
            assertThat(second.toolEvidenceSections()).isNotEmpty();
            assertThat(reads.get()).isGreaterThanOrEqualTo(2);
        } finally {
            if (id != null && !chats.get(id).busy()) chats.remove(id);
            server.stop(0);
        }
    }

    private static byte[] scannedPdf() throws Exception {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            for (var code : List.of("ALPHA-726", "BETA-918")) {
                var image = new BufferedImage(1000, 200, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 1000, 200);
                graphics.setColor(Color.BLACK); graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
                graphics.drawString("DOCUMENT-CODE: " + code, 30, 100); graphics.dispose();
                var page = new PDPage(new PDRectangle(500, 100)); document.addPage(page);
                try (var content = new PDPageContentStream(document, page)) {
                    content.drawImage(LosslessFactory.createFromImage(document, image), 0, 0, 500, 100);
                }
            }
            document.save(bytes); return bytes.toByteArray();
        }
    }

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
