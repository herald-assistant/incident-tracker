package pl.mkn.tdw.features.systemassistant.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.common.documents.DocumentAttachmentPreparer;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConversation;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantException;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantAttachment;
import pl.mkn.tdw.features.systemassistant.ai.SystemAssistantAiPort;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.io.InputStream;

/** Owns the transcript format; the workspace owns files and the shared history index. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
public class SystemAssistantConversationHistory {
    public static final String FEATURE = "system-assistant";
    private static final String SCHEMA = "tdw.system-assistant-conversation";
    private final LocalAnalysisRunStore store;
    private final ObjectMapper mapper;
    private final LocalWorkspaceProperties workspace;

    public record Archive(String schema, int version, Instant createdAt, SystemAssistantConversation conversation) { }

    public Archive load(String id) {
        requireEnabled();
        synchronized (store) {
            if (store.listRuns().stream().noneMatch(entry -> entry.feature().equals(FEATURE) && entry.analysisId().equals(id)))
                throw new SystemAssistantException("CONVERSATION_NOT_FOUND", UserFacingErrorType.NOT_FOUND, "Rozmowa nie istnieje lub została usunięta z historii.");
            try {
                var record = store.findById(id).orElseThrow();
                var archive = mapper.treeToValue(record.exportEnvelope(), Archive.class);
                if (!SCHEMA.equals(archive.schema()) || archive.version() != 1 || archive.createdAt() == null
                        || archive.conversation() == null || !id.equals(archive.conversation().id())
                        || archive.conversation().messages() == null) throw new IllegalArgumentException();
                var messages = archive.conversation().messages();
                if (messages.size() % 2 != 0) throw new IllegalArgumentException();
                for (var i = 0; i < messages.size(); i++) {
                    var message = messages.get(i);
                    if (message == null || message.id() == null || message.content() == null || message.createdAt() == null
                            || !(i % 2 == 0 ? "USER" : "ASSISTANT").equals(message.role())
                            || !List.of("COMPLETED", "FAILED", "IN_PROGRESS").contains(message.status())
                            || ("IN_PROGRESS".equals(message.status()) && i != messages.size() - 1)) throw new IllegalArgumentException();
                }
                if (archive.conversation().busy() != messages.stream().anyMatch(message -> "IN_PROGRESS".equals(message.status())))
                    throw new IllegalArgumentException();
                var attachmentState = record.storeSnapshot() == null ? null : record.storeSnapshot().state().get("attachments");
                List<SystemAssistantAttachment> attachments = attachmentState == null ? List.of()
                        : mapper.convertValue(attachmentState, new TypeReference<List<SystemAssistantAttachment>>() { });
                var conversation = archive.conversation();
                return new Archive(archive.schema(), archive.version(), archive.createdAt(),
                        new SystemAssistantConversation(conversation.id(), conversation.busy(), conversation.pageContext(),
                                conversation.messages(), attachments));
            } catch (Exception exception) {
                throw new SystemAssistantException("CONVERSATION_UNAVAILABLE", UserFacingErrorType.SERVICE_UNAVAILABLE,
                        "Nie udało się odczytać zapisu rozmowy. Nie odtworzono innej wersji.");
            }
        }
    }

    public List<String> interruptedIds() {
        return store.listRuns().stream().filter(entry -> entry.feature().equals(FEATURE) && entry.status().equals("IN_PROGRESS"))
                .map(LocalAnalysisRunIndexEntry::analysisId).toList();
    }

    /** Returns the generated title at this checkpoint so the next save can recognize an operator rename. */
    public String save(Instant createdAt, SystemAssistantConversation conversation, String previousDefaultName) {
        requireEnabled();
        try {
            synchronized (store) {
                var defaultName = defaultName(conversation);
                var name = defaultName;
                if (previousDefaultName != null) {
                    var entry = store.listRuns().stream().filter(item -> item.analysisId().equals(conversation.id())).findFirst().orElseThrow();
                    if (!entry.name().equals(previousDefaultName)) name = entry.name();
                }
                var last = conversation.messages().isEmpty() ? null : conversation.messages().get(conversation.messages().size() - 1);
                var status = conversation.busy() ? "IN_PROGRESS" : last == null ? "PENDING" : last.status();
                ObjectNode envelope = mapper.valueToTree(new Archive(SCHEMA, 1, createdAt, conversation));
                ((ObjectNode) envelope.get("conversation")).remove("attachments");
                var attachments = new LocalAnalysisRunStoreSnapshot(
                        mapper.valueToTree(Map.of("attachments", conversation.attachments())), Instant.now(), FEATURE, 0);
                LocalAnalysisRunSnapshotWriter.save(store, envelope, null, attachments,
                        new LocalAnalysisRunSnapshotWriter.Metadata(conversation.id(), FEATURE, name, status, createdAt, Instant.now(),
                                conversation.busy() || last == null ? null : last.completedAt()));
                return defaultName;
            }
        } catch (RuntimeException exception) {
            throw new SystemAssistantException("CONVERSATION_SAVE_FAILED", UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "Nie udało się zapisać rozmowy w historii. Nie ponawiaj operacji w systemie; ustal stan rozmowy po przywróceniu zapisu.");
        }
    }

    public void delete(String id) { load(id); store.delete(id); }

    /** Called under the same run operation lease as send/delete. Files are retained until run deletion. */
    public SystemAssistantAttachment writeAttachment(String id, String originalName, InputStream input) {
        requireEnabled();
        var name = attachmentName(originalName);
        var extension = extension(name);
        var attachmentId = UUID.randomUUID().toString();
        var directory = new LocalWorkspacePaths(workspace).runDirectory(id).resolve("attachments");
        try {
            Files.createDirectories(directory);
            var path = directory.resolve(attachmentId + "." + extension);
            try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                input.transferTo(output);
            }
            return new SystemAssistantAttachment(attachmentId, name,
                    DocumentAttachmentPreparer.mimeTypeForName(name), Files.size(path), null);
        } catch (IOException exception) {
            throw new SystemAssistantException("ATTACHMENT_SAVE_FAILED", UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "Nie udało się zapisać załączników rozmowy.");
        }
    }

    public List<SystemAssistantAiPort.Attachment> readAttachments(String id, List<SystemAssistantAttachment> attachments) {
        var result = new ArrayList<SystemAssistantAiPort.Attachment>();
        var directory = new LocalWorkspacePaths(workspace).runDirectory(id).resolve("attachments");
        try {
            for (var attachment : attachments) {
                var file = directory.resolve(UUID.fromString(attachment.id()) + "." + extension(attachment.name()));
                result.add(new SystemAssistantAiPort.Attachment(attachment.id(), attachment.messageId(), attachment.name(),
                        attachment.mimeType(), Files.readAllBytes(file)));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new SystemAssistantException("ATTACHMENT_UNAVAILABLE", UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "Nie udało się odczytać zapisanego załącznika rozmowy.");
        }
    }

    private static String attachmentName(String original) {
        if (original == null) throw invalidAttachment();
        var name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isBlank() || name.chars().anyMatch(Character::isISOControl)) throw invalidAttachment();
        return name;
    }

    private static String extension(String name) {
        var extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (DocumentAttachmentPreparer.mimeTypeForName(name) == null) throw invalidAttachment();
        return extension;
    }

    private static SystemAssistantException invalidAttachment() {
        return new SystemAssistantException("ATTACHMENT_INVALID", UserFacingErrorType.BAD_REQUEST,
                "Wybierz pliki PNG, JPEG, WebP, GIF, BMP, PDF lub DOCX.");
    }

    private void requireEnabled() {
        if (!workspace.isEnabled()) throw new SystemAssistantException("CONVERSATION_HISTORY_DISABLED", UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Historia rozmów wymaga włączonego lokalnego workspace TDW.");
    }

    static String defaultName(SystemAssistantConversation conversation) {
        var first = conversation.messages().stream().filter(message -> "USER".equals(message.role())).findFirst();
        var text = first.map(message -> message.content()).orElse(conversation.pageContext() == null ? "Nowa rozmowa" : conversation.pageContext().title());
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? "Nowa rozmowa" : text.substring(0, Math.min(120, text.length()));
    }
}
