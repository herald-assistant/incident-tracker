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
