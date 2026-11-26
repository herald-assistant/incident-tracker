package pl.mkn.tdw.features.systemassistant.chat;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.systemassistant.ai.SystemAssistantAiPort;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantProfileResources;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConversation;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantAttachment;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantException;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantMessageRequest;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantLoginRequiredException;
import pl.mkn.tdw.integrations.httpauth.OidcLoginPort;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
public class SystemAssistantConversationService {
    private final SystemAssistantConfigurationService configurations;
    private final SystemAssistantAiPort ai;
    private final SystemAssistantConversationHistory history;
    private final LocalAnalysisRunOperationGuard operationGuard;
    private final OidcLoginPort oidc;
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final ExecutorService worker = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
        var thread = new Thread(runnable, "system-assistant-turn");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void recoverInterruptedTurns() {
        for (var id : history.interruptedIds()) {
            try {
                get(id);
            } catch (RuntimeException exception) {
                log.warn("Could not recover interrupted System Assistant conversation id={}", id);
            }
        }
    }

    public SystemAssistantConversation create(SystemAssistantConversation.PageContext page) {
        validate(page);
        var conversation = new Conversation(UUID.randomUUID().toString(), page, Instant.now(), List.of());
        persist(conversation);
        return conversation.snapshot();
    }

    public SystemAssistantConversation get(String id) {
        var conversation = find(id, false);
        synchronized (conversation) {
            if (!conversation.busy() && conversation.dirty) {
                try (var recoveryLease = lease(id)) {
                    history.load(id);
                    persist(conversation);
                    releaseIfSaved(conversation);
                }
            }
            return conversation.snapshot();
        }
    }

    public void remove(String id) {
        try (var lease = lease(id)) {
            history.delete(id);
            conversations.remove(id);
        }
    }

    public SystemAssistantConversation attach(String id, String name, InputStream content) {
        try (var lease = lease(id)) {
            var conversation = find(id, true);
            synchronized (conversation) {
                requireIdleAndPersistPending(conversation);
                conversation.attachments.add(history.writeAttachment(id, name, content));
                conversations.put(id, conversation);
                persist(conversation);
                releaseIfSaved(conversation);
                return conversation.snapshot();
            }
        }
    }

    public SystemAssistantConversation send(String id, SystemAssistantMessageRequest request) {
        ai.requireReady();
        var lease = lease(id);
        try {
            var conversation = find(id, true);
            synchronized (conversation) {
                requireIdleAndPersistPending(conversation);
                var profile = configurations.snapshot().forProfile(request.profileId());
                requireApiAuthentication(profile);
                var previousAttachments = List.copyOf(conversation.attachments);
                conversation.begin(request.message());
                conversations.put(id, conversation);
                try {
                    persist(conversation);
                    worker.execute(() -> executeTurn(conversation, profile, request, lease));
                } catch (RuntimeException failure) {
                    conversation.undoAcceptance(previousAttachments);
                    if (failure instanceof RejectedExecutionException) {
                        persist(conversation);
                        releaseIfSaved(conversation);
                        throw error("AI_BUSY", UserFacingErrorType.SERVICE_UNAVAILABLE, "Kolejka lokalna jest pełna. Spróbuj później.");
                    }
                    throw failure;
                }
                return conversation.snapshot();
            }
        } catch (RuntimeException failure) {
            lease.close();
            throw failure;
        }
    }

    private void executeTurn(Conversation conversation, SystemAssistantProfileResources profile,
                             SystemAssistantMessageRequest request, LocalAnalysisRunOperationGuard.Lease lease) {
        var assistant = conversation.active;
        try {
            var reply = ai.answer(assistant.id(), profile, conversation.page, List.copyOf(conversation.messages), request,
                    section -> {
                        synchronized (conversation) {
                            assistant.addToolEvidence(section);
                            persist(conversation);
                        }
                    },
                    activity -> {
                        synchronized (conversation) {
                            assistant.addActivity(activity);
                            persist(conversation);
                        }
                    }, history.readAttachments(conversation.id, conversation.attachments));
            synchronized (conversation) {
                assistant.complete(reply.content(), null, reply.usage());
            }
        } catch (RuntimeException failure) {
            synchronized (conversation) {
                if (failure instanceof UserFacingApplicationException userError) assistant.fail(userError.code(), userError.getMessage());
                else assistant.fail("AI_FAILED", "Nie udało się zakończyć tury AI. Sprawdź połączenie i ustawienia Copilota. Wykonane operacje pozostają w przebiegu; nie ponowiono zapisów.");
            }
        } finally {
            synchronized (conversation) {
                conversation.messages.add(assistant.snapshot());
                conversation.active = null;
                try {
                    persist(conversation);
                } catch (RuntimeException failure) {
                    log.warn("System Assistant transcript awaits storage recovery id={}", conversation.id);
                } finally {
                    lease.close();
                    releaseIfSaved(conversation);
                }
            }
        }
    }

    private void requireIdleAndPersistPending(Conversation conversation) {
        if (conversation.busy()) throw error("CHAT_BUSY", UserFacingErrorType.CONFLICT,
                "Poczekaj na zakończenie poprzedniej odpowiedzi.");
        if (conversation.dirty) {
            persist(conversation);
            releaseIfSaved(conversation);
        }
    }

    private void releaseIfSaved(Conversation conversation) {
        if (!conversation.busy() && !conversation.dirty) conversations.remove(conversation.id, conversation);
    }

    private LocalAnalysisRunOperationGuard.Lease lease(String id) {
        return operationGuard.tryAcquire(id).orElseThrow(() -> error("CHAT_BUSY", UserFacingErrorType.CONFLICT, "Poczekaj na zakończenie tury lub innej operacji na rozmowie."));
    }

    private void requireApiAuthentication(SystemAssistantProfileResources configuration) {
        var profiles = configuration.executions().stream().map(execution -> execution.authentication())
                .filter(Objects::nonNull).filter(profile -> "OIDC".equals(profile.type())).distinct().toList();
        for (var profile : profiles) {
            try {
                oidc.authorization(profile); // Refresh before accepting a turn, never a business request.
            } catch (HttpAuthException failure) {
                throw new SystemAssistantLoginRequiredException(profile.id());
            }
        }
    }

    private Conversation find(String id, boolean ownsLease) {
        var current = conversations.get(id);
        if (current != null) {
            try {
                history.load(id);
            } catch (SystemAssistantException exception) {
                if ("CONVERSATION_NOT_FOUND".equals(exception.code())) conversations.remove(id, current);
                throw exception;
            }
            return current;
        }
        try (var readLease = ownsLease ? null : lease(id)) {
            current = conversations.get(id);
            if (current != null) return current;
            var stored = history.load(id);
            var conversation = new Conversation(id, stored.conversation().pageContext(), stored.createdAt(), stored.conversation().messages());
            conversation.attachments.addAll(stored.conversation().attachments());
            conversation.persistedDefaultName = SystemAssistantConversationHistory.defaultName(stored.conversation());
            validate(conversation.page);
            if (stored.conversation().busy() || conversation.messages.stream().anyMatch(message -> "IN_PROGRESS".equals(message.status()))) {
                for (var i = 0; i < conversation.messages.size(); i++) {
                    var message = conversation.messages.get(i);
                    if (!"ASSISTANT".equals(message.role()) || !"IN_PROGRESS".equals(message.status())) continue;
                    var now = Instant.now();
                    conversation.messages.set(i, new AnalysisChatMessageResponse(message.id(), message.role(), "FAILED", message.content(),
                            "TURN_INTERRUPTED", "Tura została przerwana przez restart TDW. Nie ponowiono operacji; sprawdź ich stan przed dalszą pracą.",
                            message.createdAt(), now, now, message.toolEvidenceSections(), message.aiActivityEvents(), message.toolFeedback(), message.prompt(), message.usage()));
                }
                persist(conversation);
            }
            return conversation;
        }
    }

    private void persist(Conversation conversation) {
        conversation.dirty = true;
        conversation.persistedDefaultName = history.save(conversation.createdAt, conversation.snapshot(), conversation.persistedDefaultName);
        conversation.dirty = false;
    }

    private void validate(SystemAssistantConversation.PageContext page) {
        if (page == null) return;
        try {
            var origin = URI.create(page.origin());
            if (!Set.of("http", "https").contains(origin.getScheme()) || origin.getHost() == null
                    || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                    || !origin.getPath().isEmpty() || page.pathname() == null || !page.pathname().startsWith("/")
                    || page.pathname().length() > 2000 || page.pathname().contains("?") || page.pathname().contains("#")
                    || page.title() == null || page.title().length() > 200 || page.origin().length() > 300)
                throw new IllegalArgumentException();
            Instant.parse(page.capturedAt());
        } catch (RuntimeException ex) {
            throw error("PAGE_CONTEXT_INVALID", UserFacingErrorType.BAD_REQUEST, "Nieprawidłowy kontekst strony.");
        }
    }

    private SystemAssistantException error(String code, UserFacingErrorType type, String message) {
        return new SystemAssistantException(code, type, message);
    }

    @PreDestroy
    public void close() {
        worker.shutdown();
    }

    private static class Conversation {
        final String id;
        final SystemAssistantConversation.PageContext page;
        final Instant createdAt;
        final List<AnalysisChatMessageResponse> messages;
        final List<SystemAssistantAttachment> attachments = new ArrayList<>();
        AnalysisChatMessageState active;
        boolean dirty;
        String persistedDefaultName;

        Conversation(String id, SystemAssistantConversation.PageContext page, Instant createdAt, List<AnalysisChatMessageResponse> messages) {
            this.id = id;
            this.page = page;
            this.createdAt = createdAt;
            this.messages = new ArrayList<>(messages);
        }

        void begin(String message) {
            var user = AnalysisChatMessageState.completedUser(UUID.randomUUID().toString(), message, Instant.now());
            attachments.replaceAll(file -> file.messageId() != null ? file
                    : new SystemAssistantAttachment(file.id(), file.name(), file.mimeType(), file.size(), user.id()));
            messages.add(user.snapshot());
            active = AnalysisChatMessageState.inProgressAssistant(UUID.randomUUID().toString(), Instant.now());
        }

        void undoAcceptance(List<SystemAssistantAttachment> previousAttachments) {
            messages.remove(messages.size() - 1);
            active = null;
            attachments.clear();
            attachments.addAll(previousAttachments);
        }

        boolean busy() {
            return active != null;
        }

        synchronized SystemAssistantConversation snapshot() {
            var transcript = new ArrayList<>(messages);
            if (active != null) transcript.add(active.snapshot());
            return new SystemAssistantConversation(id, busy(), page, List.copyOf(transcript), List.copyOf(attachments));
        }
    }
}
