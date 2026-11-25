package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.mkn.tdw.api.ApiExceptionHandler;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.*;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.features.systemassistant.ai.*;
import pl.mkn.tdw.features.systemassistant.api.SystemAssistantConversationController;
import pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationService;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.*;
import pl.mkn.tdw.integrations.httpauth.OidcLoginPort;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemAssistantAttachmentsTest {
    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private SystemAssistantConversationService service(SystemAssistantAiPort ai) {
        return new SystemAssistantConversationService(SystemAssistantTestSupport.configuration((id, yaml) -> List.of()), ai,
                SystemAssistantTestSupport.history(workspace), new LocalAnalysisRunOperationGuard(), mock(OidcLoginPort.class));
    }

    @Test void bindsNewFilesToTheirFirstAcceptedMessageAndRestoresBindingsWithoutExportingThem() throws Exception {
        var ai = mock(SystemAssistantAiPort.class);
        when(ai.answer(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SystemAssistantAiPort.Reply("Gotowe", null));
        var service = service(ai);
        String id;
        String firstMessage;
        String secondMessage;
        try {
            id = service.create(null).id();
            service.attach(id, "first.pdf", new ByteArrayInputStream(new byte[]{1}));
            assertThat(service.get(id).attachments().get(0).messageId()).isNull();
            var first = service.send(id, new SystemAssistantMessageRequest("Pierwsze", "test_profile", null, null));
            firstMessage = first.messages().get(0).id();
            assertThat(first.attachments().get(0).messageId()).isEqualTo(firstMessage);
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());

            service.attach(id, "second.png", new ByteArrayInputStream(new byte[]{2}));
            var second = service.send(id, new SystemAssistantMessageRequest("Drugie", "test_profile", null, null));
            secondMessage = second.messages().get(2).id();
            assertThat(second.attachments()).extracting(SystemAssistantAttachment::messageId)
                    .containsExactly(firstMessage, secondMessage);
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            service.send(id, new SystemAssistantMessageRequest("Trzecie", "test_profile", null, null));
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
        } finally { service.close(); }
        var reopened = service(ai);
        try {
            assertThat(reopened.get(id).attachments()).extracting(SystemAssistantAttachment::messageId)
                    .containsExactly(firstMessage, secondMessage);
            var stored = mapper.readTree(workspace.resolve("runs").resolve(id).resolve("run.json").toFile());
            assertThat(stored.at("/storeSnapshot/state/attachments/0/messageId").asText()).isEqualTo(firstMessage);
            assertThat(stored.path("exportEnvelope").toString()).doesNotContain("messageId", "attachments", "first.pdf", "second.png");
        } finally { reopened.close(); }
    }

    @Test void rejectedWorkLeavesAttachmentsWaitingForTheNextMessage() {
        var service = service(mock(SystemAssistantAiPort.class));
        try {
            var id = service.create(null).id();
            service.attach(id, "waiting.pdf", new ByteArrayInputStream(new byte[]{1}));
            ((ExecutorService) org.springframework.test.util.ReflectionTestUtils.getField(service, "worker")).shutdown();
            assertThatThrownBy(() -> service.send(id, new SystemAssistantMessageRequest("Pytanie", "test_profile", null, null)))
                    .isInstanceOfSatisfying(SystemAssistantException.class, error -> assertThat(error.code()).isEqualTo("AI_BUSY"));
            assertThat(service.get(id).messages()).isEmpty();
            assertThat(service.get(id).attachments().get(0).messageId()).isNull();
            assertThat(SystemAssistantTestSupport.history(workspace).load(id).conversation().attachments().get(0).messageId()).isNull();
        } finally { service.close(); }
    }

    @Test void streamsLargeFilesToUniqueNamesRestoresMetadataAndExcludesAttachmentsFromExport() throws Exception {
        var ai = mock(SystemAssistantAiPort.class);
        String id;
        var body = new byte[2 * 1024 * 1024];
        Arrays.fill(body, (byte) 42);
        var first = service(ai);
        try {
            id = first.create(null).id();
            var mvc = MockMvcBuilders.standaloneSetup(new SystemAssistantConversationController(first))
                    .setControllerAdvice(new ApiExceptionHandler()).build();
            for (var i = 0; i < 2; i++) mvc.perform(post("/api/system-assistant/conversations/{id}/attachments", id)
                            .param("name", "C:\\fakepath\\report.PDF").contentType("application/octet-stream").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.attachments[0].name").value("report.PDF"))
                    .andExpect(jsonPath("$.attachments[0].size").value(body.length))
                    .andExpect(jsonPath("$.attachments[0].mimeType").value("application/pdf"));
            assertThat(first.get(id).attachments()).hasSize(2).extracting(SystemAssistantAttachment::id).doesNotHaveDuplicates();
            try (var files = Files.list(workspace.resolve("runs").resolve(id).resolve("attachments"))) {
                assertThat(files.toList()).hasSize(2).allSatisfy(file -> assertThat(file).hasBinaryContent(body));
            }
            var stored = mapper.readTree(workspace.resolve("runs").resolve(id).resolve("run.json").toFile());
            assertThat(stored.at("/storeSnapshot/state/attachments").size()).isEqualTo(2);
            assertThat(stored.path("exportEnvelope").toString()).doesNotContain("attachments", "report.PDF");
            verifyNoInteractions(ai);
        } finally { first.close(); }
        var reopened = service(ai);
        try {
            assertThat(reopened.get(id).attachments()).hasSize(2);
            reopened.remove(id);
            assertThat(workspace.resolve("runs").resolve(id)).doesNotExist();
        } finally { reopened.close(); }
    }

    @Test void passesAllDurableAttachmentsToEveryTurnAndRejectsUploadsWhileBusy() throws Exception {
        var ai = mock(SystemAssistantAiPort.class);
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var seen = new CopyOnWriteArrayList<List<SystemAssistantAiPort.Attachment>>();
        when(ai.answer(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            List<SystemAssistantAiPort.Attachment> files = call.getArgument(7);
            seen.add(files); entered.countDown();
            assertThat(Files.readString(workspace.resolve("runs").resolve(SystemAssistantTestSupport.history(workspace)
                    .interruptedIds().get(0)).resolve("run.json"))).contains("report.pdf");
            release.await(5, TimeUnit.SECONDS);
            return new SystemAssistantAiPort.Reply("Gotowe", null);
        });
        var service = service(ai);
        try {
            var id = service.create(null).id();
            service.attach(id, "report.pdf", new ByteArrayInputStream(new byte[]{1, 2, 3}));
            service.send(id, new SystemAssistantMessageRequest("Pierwsze pytanie", "test_profile", null, null));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.attach(id, "next.png", new ByteArrayInputStream(new byte[]{4})))
                    .isInstanceOfSatisfying(SystemAssistantException.class, error -> assertThat(error.code()).isEqualTo("CHAT_BUSY"));
            release.countDown();
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            service.send(id, new SystemAssistantMessageRequest("Drugie pytanie", "test_profile", null, null));
            await().atMost(5, TimeUnit.SECONDS).until(() -> !service.get(id).busy());
            assertThat(seen).hasSize(2).allSatisfy(files -> {
                assertThat(files).hasSize(1);
                assertThat(files.get(0).content()).containsExactly(1, 2, 3);
                assertThat(files.get(0).name()).isEqualTo("report.pdf");
            });
        } finally { release.countDown(); service.close(); }
    }

    @Test void acceptsOnlyAgreedExtensionsAndCannotCreateFilesForUnknownConversations() {
        var service = service(mock(SystemAssistantAiPort.class));
        try {
            var id = service.create(null).id();
            for (var extension : List.of("png", "jpg", "jpeg", "webp", "gif", "pdf", "docx"))
                service.attach(id, "test." + extension, new ByteArrayInputStream(new byte[]{1}));
            assertThat(service.get(id).attachments()).hasSize(7);
            assertThatThrownBy(() -> service.attach(id, "test.exe", new ByteArrayInputStream(new byte[]{1})))
                    .isInstanceOfSatisfying(SystemAssistantException.class, error -> assertThat(error.code()).isEqualTo("ATTACHMENT_INVALID"));
            assertThatThrownBy(() -> service.attach("unknown", "test.pdf", new ByteArrayInputStream(new byte[]{1})))
                    .isInstanceOf(SystemAssistantException.class);
            assertThat(workspace.resolve("runs/unknown")).doesNotExist();
        } finally { service.close(); }
    }

    @Test void featureAdapterSendsNativeBlobsWithoutChangingSessionPolicy() {
        var preparation = mock(CopilotRunPreparationService.class);
        var gateway = mock(CopilotSdkExecutionGateway.class);
        var options = new MessageOptions();
        var prepared = new CopilotPreparedSession("turn", new CopilotClientOptions(), new SessionConfig(), options, "prompt", Map.of());
        when(preparation.prepare(any())).thenReturn(prepared);
        when(gateway.execute(prepared)).thenReturn(new CopilotExecutionResult("Gotowe", null));
        var runner = new SystemAssistantCopilotRunner(() -> "fixture", new CopilotSdkToolFactory(List.of(), mapper, List.of(), null),
                new OpenApiToolSetFactory((execution, arguments) -> null, mapper), preparation, gateway,
                new SystemAssistantPrompt(mapper), CopilotSessionCleanup.NO_OP);
        runner.answer("turn", new SystemAssistantConfigurationService.Snapshot(SystemAssistantTestSupport.config(List.of(), List.of()), List.of(), Map.of()),
                null, List.of(), new SystemAssistantMessageRequest("Pytanie", "test_profile", null, null), ignored -> {}, ignored -> {},
                List.of(new SystemAssistantAiPort.Attachment("image.png", "image/png", new byte[]{1, 2, 3})));
        assertThat(options.getAttachments()).singleElement().isInstanceOfSatisfying(BlobAttachment.class, blob -> {
            assertThat(blob.getDisplayName()).isEqualTo("image.png");
            assertThat(blob.getMimeType()).isEqualTo("image/png");
            assertThat(Base64.getDecoder().decode(blob.getData())).containsExactly(1, 2, 3);
        });
    }
}
