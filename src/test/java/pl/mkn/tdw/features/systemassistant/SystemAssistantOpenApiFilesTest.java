package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolInvocation;
import com.github.copilot.rpc.ToolResultObject;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.*;
import pl.mkn.tdw.aiplatform.copilot.tools.context.*;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.events.*;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class SystemAssistantOpenApiFilesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void readsDetectedHttpFilesThroughSdkAndReportsUnreadableDocumentsWithoutRetry() throws Exception {
        var bytes = new java.util.concurrent.atomic.AtomicReference<>(OpenApiFileFixtures.pdf("CRM-831"));
        var mime = new java.util.concurrent.atomic.AtomicReference<String>("application/octet-stream");
        var calls = new AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            calls.incrementAndGet();
            if (mime.get() != null) exchange.getResponseHeaders().set("Content-Type", mime.get());
            var content = bytes.get();
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        server.start();
        try {
            var yaml = OpenApiFileFixtures.yaml("http://127.0.0.1:" + server.getAddress().getPort())
                    .replace("application/pdf:", "application/octet-stream:");
            var operation = new OpenApiImporter(mapper).parse("files", yaml).get(0);
            var executor = new pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor(
                    pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator.create(
                            org.springframework.web.client.RestClient.builder()), mapper, ignored -> null);
            var callbacks = new OpenApiToolSetFactory(executor, mapper).create(List.of(new OpenApiExecution(operation, null)));
            var events = new ArrayList<CopilotToolInvocationEvent>();
            var handler = new CopilotToolInvocationHandler(mapper, new CopilotToolContextFactory(), List.of(),
                    new CopilotToolInvocationEventPublisher(event -> events.add((CopilotToolInvocationEvent) event)));
            var tool = new CopilotSdkToolFactory(List.of(), mapper, List.of(), handler).createToolDefinitions(
                    new CopilotToolSessionContext("files", "files-session", Map.of()),
                    CopilotToolDescriptionContext.profile("system-assistant"), callbacks).get(0);
            var args = mapper.valueToTree(Map.of("path", Map.of("format", "test")));
            String previousPdf = null;
            for (var format : List.of("pdf", "pdf", "docx", "png", "bmp", "brokenBmp", "brokenPdf", "brokenDocx")) {
                if (format.equals("pdf")) bytes.set(OpenApiFileFixtures.pdf("CRM-" + calls.get()));
                if (format.equals("docx")) { mime.set(null); bytes.set(OpenApiFileFixtures.docx("CRM-682")); }
                if (format.equals("png")) { mime.set("application/octet-stream"); bytes.set(OpenApiFileFixtures.png("CRM-215")); }
                if (format.equals("bmp")) bytes.set(OpenApiFileFixtures.bmp("CRM-215"));
                if (format.equals("brokenBmp")) bytes.set(Arrays.copyOf(bytes.get(), 14));
                if (format.equals("brokenPdf")) bytes.set("%PDF-1.7\ninvalid".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                if (format.equals("brokenDocx")) {
                    var archiveBytes = new java.io.ByteArrayOutputStream();
                    try (var zip = new java.util.zip.ZipOutputStream(archiveBytes)) {
                        zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
                        zip.write("<broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        zip.closeEntry();
                    }
                    bytes.set(archiveBytes.toByteArray());
                }
                var before = calls.get();
                var result = tool.handler().invoke(new ToolInvocation().setSessionId("files-session")
                        .setToolName(tool.name()).setToolCallId("call-" + before).setArguments(args)).join();
                assertThat(calls.get()).isEqualTo(before + 1);
                if (format.startsWith("broken")) {
                    var text = mapper.valueToTree(result);
                    assertThat(text.path("body").path("contentType").asText()).isNotBlank();
                    assertThat(text.path("fileDelivery").asText()).isEqualTo("FAILED");
                    assertThat(text.path("outcome").asText()).isEqualTo("RESPONSE_RECEIVED");
                    assertThat(text.path("statusCode").asInt()).isEqualTo(200);
                } else {
                    var reply = (ToolResultObject) result;
                    assertThat(reply.textResultForLlm()).contains("CURRENT_TURN_ONLY").doesNotContain("base64");
                    assertThat(reply.binaryResultsForLlm()).hasSize(1);
                    var data = reply.binaryResultsForLlm().get(0).data();
                    assertThat(reply.textResultForLlm()).doesNotContain(data);
                    assertThat(events.toString()).doesNotContain(data);
                    if (format.equals("docx")) assertThat(reply.textResultForLlm()).contains("CRM-682");
                    if (format.equals("bmp")) {
                        assertThat(reply.textResultForLlm()).contains("image/bmp");
                        assertThat(reply.binaryResultsForLlm().get(0).mimeType()).isEqualTo("image/png");
                        assertThat(Base64.getDecoder().decode(data)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
                    }
                    if (format.equals("pdf")) {
                        assertThat(data).isNotEqualTo(previousPdf);
                        previousPdf = data;
                    }
                }
            }
        } finally { server.stop(0); }
    }

    @Test void returnsPdfImagesThroughSdkBridgeAndRunsTheToolAgainWithoutPersistingBytes() throws Exception {
        var pdf = OpenApiFileFixtures.pdf("PDF-831");
        var calls = new AtomicInteger();
        var events = new ArrayList<CopilotToolInvocationEvent>();
        var tool = tool("application/pdf", pdf, calls, events);
        for (var i = 0; i < 2; i++) {
            var reply = (ToolResultObject) tool.handler().invoke(new ToolInvocation().setSessionId("files-session")
                    .setToolName(tool.name()).setToolCallId("call-" + i).setArguments(mapper.valueToTree(Map.of()))).join();
            assertThat(reply.textResultForLlm()).contains("CURRENT_TURN_ONLY", "application/pdf");
            assertThat(reply.binaryResultsForLlm()).singleElement().satisfies(binary -> {
                assertThat(binary.mimeType()).isEqualTo("image/jpeg");
                assertThat(binary.type()).isEqualTo("image");
                assertThat(binary.description()).contains("strona 1/1");
                var bytes = Base64.getDecoder().decode(binary.data());
                assertThat(ImageIO.read(new ByteArrayInputStream(bytes)).getWidth()).isEqualTo(1000);
                assertThat(reply.textResultForLlm()).doesNotContain(binary.data());
                assertThat(events.toString()).doesNotContain(binary.data());
            });
        }
        assertThat(calls).hasValue(2);
        assertThat(events.stream().filter(CopilotToolInvocationFinishedEvent.class::isInstance)).hasSize(2);
    }

    @Test void exposesDocxTextAndEmbeddedImageWithoutReturningAnUnreadableNativeFile() throws Exception {
        var tool = tool("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                OpenApiFileFixtures.docx("WORD-682"), new AtomicInteger(), new ArrayList<>());
        var reply = (ToolResultObject) tool.handler().invoke(new ToolInvocation().setSessionId("files-session")
                .setToolName(tool.name()).setToolCallId("docx").setArguments(mapper.valueToTree(Map.of()))).join();
        assertThat(reply.textResultForLlm()).contains("WORD-682", "documentText");
        assertThat(reply.binaryResultsForLlm()).singleElement().satisfies(binary -> {
            assertThat(binary.type()).isEqualTo("image");
            assertThat(binary.mimeType()).isEqualTo("image/png");
        });
    }

    @Test void corruptPdfReportsDeliveryFailureWithoutRetryingTheCompletedHttpOperation() throws Exception {
        var calls = new AtomicInteger();
        var tool = tool("application/pdf", new byte[]{1, 2, 3}, calls, new ArrayList<>());
        var reply = tool.handler().invoke(new ToolInvocation().setSessionId("files-session")
                .setToolName(tool.name()).setToolCallId("invalid").setArguments(mapper.valueToTree(Map.of()))).join();
        assertThat(mapper.valueToTree(reply).path("fileDelivery").asText()).isEqualTo("FAILED");
        assertThat(mapper.valueToTree(reply).path("outcome").asText()).isEqualTo("RESPONSE_RECEIVED");
        assertThat(calls).hasValue(1);
    }

    private com.github.copilot.rpc.ToolDefinition tool(String mime, byte[] bytes, AtomicInteger calls,
                                                     List<CopilotToolInvocationEvent> events) {
        var operation = new OpenApiImporter(mapper).parse("files", OpenApiFileFixtures.yaml("http://example.invalid")).get(0);
        var callbacks = new OpenApiToolSetFactory((execution, args) -> {
            calls.incrementAndGet();
            return new OpenApiResult("RESPONSE_RECEIVED", 200, mapper.createObjectNode().put("contentType", mime), "HTTP completed",
                    new OpenApiFile("document." + OpenApiFile.EXTENSIONS.get(mime), mime, bytes));
        }, mapper).create(List.of(new OpenApiExecution(operation, null)));
        var handler = new CopilotToolInvocationHandler(mapper, new CopilotToolContextFactory(), List.of(),
                new CopilotToolInvocationEventPublisher(event -> events.add((CopilotToolInvocationEvent) event)));
        return new CopilotSdkToolFactory(List.of(), mapper, List.of(), handler).createToolDefinitions(
                new CopilotToolSessionContext("files", "files-session", Map.of()),
                CopilotToolDescriptionContext.profile("system-assistant"), callbacks).get(0);
    }
}
