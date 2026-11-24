package pl.mkn.tdw.features.systemassistant.chat;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.systemassistant.ai.SystemAssistantAiPort;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConversation;
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
                    conversations.remove(id, conversation);
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

    public SystemAssistantConversation send(String id, SystemAssistantMessageRequest request) {
        ai.requireReady();
        var lease = lease(id);
        Conversation conversation = null;
        try {
            conversation = find(id, true);
            synchronized (conversation) {
                if (conversation.busy())
                    throw error("CHAT_BUSY", UserFacingErrorType.CONFLICT, "Poczekaj na zakończenie poprzedniej odpowiedzi.");
                if (conversation.dirty) persist(conversation);
                var configuration = configurations.snapshot().forProfile(request.profileId());
                requireApiAuthentication(configuration);
                var user = AnalysisChatMessageState.completedUser(UUID.randomUUID().toString(), request.message(), Instant.now());
                var assistant = AnalysisChatMessageState.inProgressAssistant(UUID.randomUUID().toString(), Instant.now());
                conversation.messages.add(user.snapshot());
                var transcript = List.copyOf(conversation.messages);
                conversation.active = assistant;
                conversations.put(id, conversation);
                try {
                    persist(conversation); // Durable acceptance must precede any external operation.
                    var accepted = conversation;
                    worker.execute(() -> {
                        try {
                            var reply = ai.answer(assistant.id(), configuration, accepted.page, transcript, request,
                                    section -> {
                                        synchronized (accepted) {
                                            assistant.addToolEvidence(section);
                                            persist(accepted);
                                        }
                                    },
                                    activity -> {
                                        synchronized (accepted) {
                                            assistant.addActivity(activity);
                                            persist(accepted);
                                        }
                                    });
                            synchronized (accepted) {
                                assistant.complete(reply.content(), null, reply.usage());
                            }
                        } catch (RuntimeException exception) {
                            synchronized (accepted) {
                                assistant.fail(exception instanceof UserFacingApplicationException userError ? userError.code() : "AI_FAILED",
                                        exception instanceof UserFacingApplicationException ? exception.getMessage()
                                                : "Nie udało się zakończyć tury AI. Sprawdź połączenie i ustawienia Copilota. Wykonane operacje pozostają w przebiegu; nie ponowiono zapisów.");
                            }
                        } finally {
                            synchronized (accepted) {
                                accepted.messages.add(assistant.snapshot());
                                accepted.active = null;
                                try {
                                    persist(accepted);
                                } catch (RuntimeException exception) {
                                    log.warn("System Assistant transcript awaits storage recovery id={}", id);
                                } finally {
                                    lease.close();
                                    if (!accepted.dirty) conversations.remove(id, accepted);
                                }
                            }
                        }
                    });
                } catch (RuntimeException exception) {
                    conversation.messages.remove(conversation.messages.size() - 1);
                    conversation.active = null;
                    if (exception instanceof RejectedExecutionException) {
                        persist(conversation);
                        throw error("AI_BUSY", UserFacingErrorType.SERVICE_UNAVAILABLE, "Kolejka lokalna jest pełna. Spróbuj później.");
                    }
                    throw exception;
                }
                return conversation.snapshot();
            }
        } catch (RuntimeException exception) {
            lease.close();
            throw exception;
        }
    }

    private LocalAnalysisRunOperationGuard.Lease lease(String id) {
        return operationGuard.tryAcquire(id).orElseThrow(() -> error("CHAT_BUSY", UserFacingErrorType.CONFLICT, "Poczekaj na zakończenie tury lub innej operacji na rozmowie."));
    }

    private void requireApiAuthentication(SystemAssistantConfigurationService.Snapshot configuration) {
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
        history.save(conversation.createdAt, conversation.snapshot());
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
        AnalysisChatMessageState active;
        boolean dirty;

        Conversation(String id, SystemAssistantConversation.PageContext page, Instant createdAt, List<AnalysisChatMessageResponse> messages) {
            this.id = id;
            this.page = page;
            this.createdAt = createdAt;
            this.messages = new ArrayList<>(messages);
        }

        boolean busy() {
            return active != null;
        }

        synchronized SystemAssistantConversation snapshot() {
            var transcript = new ArrayList<>(messages);
            if (active != null) transcript.add(active.snapshot());
            return new SystemAssistantConversation(id, busy(), page, List.copyOf(transcript));
        }
    }
}
