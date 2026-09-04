package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.CopilotSession;
import com.github.copilot.rpc.ResumeSessionConfig;
import lombok.extern.slf4j.Slf4j;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
public final class CopilotContextTierSession {

    private static final String EVENT_TYPE = "platform.context_tier";
    private static final double LONG_WINDOW_MATCH_RATIO = 0.90D;

    private final CopilotContextTierDecision decision;
    private final Consumer<AnalysisAiActivityEvent> activitySink;
    private final CopilotEffectiveContextTierReader effectiveContextTierReader;
    private final String lifecycleId = UUID.randomUUID().toString();
    private final String initialRequestEventId = eventId("initial-requested");
    private final String runtimeRequestEventId = eventId("runtime-requested");
    private final AtomicBoolean effectiveWindowPublished = new AtomicBoolean();
    private final AtomicBoolean runtimeUpgradeRequested = new AtomicBoolean();
    private final AtomicBoolean runtimeAbortOutcomePublished = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Void>> runtimeAbortFuture = new AtomicReference<>();
    private volatile CopilotEffectiveContextTier verifiedTier;
    private volatile WindowObservation runtimeUpgradeSource;
    private volatile boolean runtimeResumeActive;

    CopilotContextTierSession(
            CopilotContextTierDecision decision,
            Consumer<AnalysisAiActivityEvent> activitySink,
            CopilotEffectiveContextTierReader effectiveContextTierReader
    ) {
        this.decision = decision;
        this.activitySink = activitySink;
        this.effectiveContextTierReader = effectiveContextTierReader;
        if (decision.useLongContextInitially()) {
            var details = details("TIER_REQUESTED", initialTrigger());
            details.put("observationSource", "SESSION_CONFIGURATION");
            publish(
                    initialRequestEventId,
                    null,
                    "COMPLETED",
                    "Żądanie rozszerzonego kontekstu",
                    initialRequestSummary(),
                    details
            );
        }
    }

    public CopilotContextTierDecision decision() {
        return decision;
    }

    public void verifyBeforeFirstMessage(CopilotSession session) {
        if (!decision.useLongContextInitially()) {
            return;
        }
        verifyExpectedLongContext(
                session,
                initialRequestEventId,
                "initial",
                initialTrigger(),
                "Prompt nie został wysłany",
                "Stan modelu SDK raportuje `long_context`; rzeczywisty limit okna zostanie pokazany z `session.usage_info`."
        );
    }

    public void observeEffectiveWindow(
            CopilotSession session,
            long tokenLimit,
            long currentTokens,
            long messagesLength
    ) {
        if (tokenLimit <= 0) {
            return;
        }

        var observation = new WindowObservation(
                tokenLimit,
                Math.max(currentTokens, 0L),
                Math.max(messagesLength, 0L),
                utilizationPercent(currentTokens, tokenLimit)
        );
        publishEffectiveWindowWhenExpected(observation);

        if (!shouldRequestRuntimeUpgrade(observation)
                || !runtimeUpgradeRequested.compareAndSet(false, true)) {
            return;
        }

        runtimeUpgradeSource = observation;
        effectiveWindowPublished.set(false);
        var details = runtimeDetails("RUNTIME_TIER_SWITCH_REQUESTED", observation);
        details.put("observationSource", "SESSION_USAGE_INFO");
        details.put("runtimeThresholdTokens", thresholdTokens(tokenLimit));
        publish(
                runtimeRequestEventId,
                null,
                "COMPLETED",
                "Przełączenie na rozszerzony kontekst",
                "Wykorzystanie bieżącego okna przekroczyło próg "
                        + percentage(decision.runtimeUsageThreshold())
                        + "; platforma przerwie turn i wznowi tę samą sesję z `long_context`.",
                details
        );

        try {
            var abort = session.abort();
            runtimeAbortFuture.set(abort != null
                    ? abort
                    : CompletableFuture.failedFuture(new IllegalStateException("Copilot SDK returned no abort future.")));
        } catch (RuntimeException failure) {
            runtimeAbortFuture.set(CompletableFuture.failedFuture(failure));
        }
    }

    public boolean runtimeUpgradeRequested() {
        return runtimeUpgradeRequested.get();
    }

    public void awaitRuntimeAbort() {
        if (!runtimeUpgradeRequested()) {
            return;
        }

        var abort = runtimeAbortFuture.get();
        if (abort == null) {
            throw runtimeAbortFailure(new IllegalStateException("Runtime context-tier abort was not started."));
        }

        try {
            abort.orTimeout(decision.switchTimeoutMillis(), TimeUnit.MILLISECONDS).join();
            if (runtimeAbortOutcomePublished.compareAndSet(false, true)) {
                publish(
                        eventId("runtime-session-aborted"),
                        runtimeRequestEventId,
                        "COMPLETED",
                        "Turn zatrzymany przed kompaktowaniem",
                        "Copilot potwierdził zatrzymanie turnu; stan sesji zostanie wznowiony z rozszerzonym kontekstem.",
                        runtimeDetails("RUNTIME_SESSION_ABORTED", runtimeUpgradeSource)
                );
            }
        } catch (RuntimeException failure) {
            throw runtimeAbortFailure(failure);
        }
    }

    public void prepareRuntimeResume(ResumeSessionConfig resumeSessionConfig, String sessionId) {
        if (!runtimeUpgradeRequested()) {
            throw new IllegalStateException("Runtime context-tier resume was requested without a threshold decision.");
        }
        if (resumeSessionConfig == null) {
            throw new IllegalStateException("Runtime context-tier resume requires ResumeSessionConfig.");
        }
        resumeSessionConfig.setContextTier(CopilotContextTierPolicy.LONG_CONTEXT);
        var details = runtimeDetails("RUNTIME_RESUME_REQUESTED", runtimeUpgradeSource);
        details.put("observationSource", "RESUME_SESSION_CONFIGURATION");
        details.put("sessionId", sessionId);
        publish(
                eventId("runtime-resume-requested"),
                runtimeRequestEventId,
                "COMPLETED",
                "Wznowienie sesji z rozszerzonym kontekstem",
                "Platforma wznowi tę samą sesję z `contextTier=long_context`, bez ponownego wysyłania initial promptu.",
                details
        );
    }

    public void verifyAfterRuntimeResume(CopilotSession session) {
        runtimeResumeActive = true;
        if (decision.preference() == CopilotContextTierPreference.AUTO) {
            var effectiveTier = effectiveContextTierReader.read(session);
            if (effectiveTier.contextTier() == null) {
                verifiedTier = effectiveTier;
                var details = details("MODEL_STATE_VERIFICATION", "RUNTIME_USAGE_THRESHOLD");
                details.put("observationSource", "SESSION_MODEL_GET_CURRENT");
                details.put("effectiveModel", effectiveTier.modelId());
                details.put("effectiveReasoningEffort", effectiveTier.reasoningEffort());
                details.put("verification", "TIER_UNCONFIRMED");
                publish(
                        eventId("runtime-model-state-unconfirmed"),
                        runtimeRequestEventId,
                        "WARNING",
                        "Tier sesji niepotwierdzony",
                        "SDK nie zwróciło `contextTier`; platforma wyśle jedną wiadomość kontynuującą i zweryfikuje pierwszy `session.usage_info`.",
                        details
                );
                return;
            }
        }
        verifyExpectedLongContext(
                session,
                runtimeRequestEventId,
                "runtime",
                "RUNTIME_USAGE_THRESHOLD",
                "Instrukcja kontynuacji nie została wysłana",
                "Wznowiona sesja raportuje `long_context`; rzeczywisty limit zostanie potwierdzony przez `session.usage_info`."
        );
    }

    private void verifyExpectedLongContext(
            CopilotSession session,
            String parentEventId,
            String eventScope,
            String trigger,
            String blockedAction,
            String successSummary
    ) {
        CopilotEffectiveContextTier effectiveTier;
        try {
            effectiveTier = effectiveContextTierReader.read(session);
        } catch (RuntimeException failure) {
            var details = details("MODEL_STATE_VERIFICATION", trigger);
            details.put("observationSource", "SESSION_MODEL_GET_CURRENT");
            details.put("failureType", rootCause(failure).getClass().getSimpleName());
            publish(
                    eventId(eventScope + "-model-state-failed"),
                    parentEventId,
                    "FAILED",
                    "Nie potwierdzono rozszerzonego kontekstu",
                    blockedAction + ", ponieważ SDK nie potwierdziło efektywnego tieru sesji.",
                    details
            );
            throw new IllegalStateException(
                    "Copilot SDK could not verify long_context before the next message.",
                    failure
            );
        }

        var details = details("MODEL_STATE_VERIFICATION", trigger);
        details.put("observationSource", "SESSION_MODEL_GET_CURRENT");
        details.put("effectiveTier", effectiveTier.contextTier());
        details.put("effectiveModel", effectiveTier.modelId());
        details.put("effectiveReasoningEffort", effectiveTier.reasoningEffort());
        if (!CopilotContextTierPolicy.LONG_CONTEXT.equals(effectiveTier.contextTier())) {
            publish(
                    eventId(eventScope + "-model-state-rejected"),
                    parentEventId,
                    "FAILED",
                    "Rozszerzony kontekst nie jest aktywny",
                    blockedAction + ", ponieważ efektywny tier sesji nie jest równy `long_context`.",
                    details
            );
            throw new IllegalStateException(
                    "Copilot SDK did not activate long_context before the next message; effective tier: "
                            + effectiveTier.contextTier()
            );
        }

        verifiedTier = effectiveTier;
        publish(
                eventId(eventScope + "-model-state-confirmed"),
                parentEventId,
                "COMPLETED",
                "Tier przyjęty przez sesję",
                successSummary,
                details
        );
    }

    private void publishEffectiveWindowWhenExpected(WindowObservation observation) {
        var expectedLongContext = decision.useLongContextInitially() && !runtimeUpgradeRequested()
                || runtimeResumeActive;
        if (!expectedLongContext || !effectiveWindowPublished.compareAndSet(false, true)) {
            return;
        }

        var trigger = runtimeResumeActive ? "RUNTIME_USAGE_THRESHOLD" : initialTrigger();
        var details = details("EFFECTIVE_WINDOW_OBSERVED", trigger);
        details.put("observationSource", "SESSION_USAGE_INFO");
        if (verifiedTier != null) {
            details.put("effectiveTier", verifiedTier.contextTier());
            details.put("effectiveModel", verifiedTier.modelId());
            details.put("effectiveReasoningEffort", verifiedTier.reasoningEffort());
        }
        addWindowDetails(details, observation);
        var runtimeWindowIncreased = runtimeResumeActive
                && runtimeUpgradeSource != null
                && observation.tokenLimit() > runtimeUpgradeSource.tokenLimit();
        details.put("verification", runtimeResumeActive
                ? runtimeWindowIncreased ? "TOKEN_LIMIT_INCREASED" : "TOKEN_LIMIT_NOT_INCREASED"
                : "TOKEN_LIMIT_OBSERVED");
        if (runtimeResumeActive) {
            details.put("runtimeUpgradeConfirmed", runtimeWindowIncreased);
        }
        publish(
                eventId(runtimeResumeActive ? "runtime-effective-window" : "initial-effective-window"),
                runtimeResumeActive ? runtimeRequestEventId : initialRequestEventId,
                runtimeResumeActive && !runtimeWindowIncreased ? "WARNING" : "COMPLETED",
                "Rzeczywisty limit kontekstu",
                runtimeResumeActive && !runtimeWindowIncreased
                        ? "Po resume Copilot nadal zgłasza limit " + observation.tokenLimit()
                        + " tokenów; kolejna próba resume nie zostanie wykonana, a SDK może dokończyć przez compaction."
                        : "Copilot zgłosił efektywny limit " + observation.tokenLimit()
                        + " tokenów przy aktualnym użyciu " + observation.currentTokens() + " tokenów.",
                details
        );
    }

    private boolean shouldRequestRuntimeUpgrade(WindowObservation observation) {
        if (!decision.policyEnabled() || runtimeUpgradeRequested()) {
            return false;
        }
        if (observation.utilizationPercent() < decision.runtimeUsageThreshold() * 100D) {
            return false;
        }
        if (decision.preference() == CopilotContextTierPreference.AUTO && !decision.modelMetadataAvailable()) {
            return false;
        }
        return !alreadyUsesExpectedLongWindow(observation.tokenLimit());
    }

    private boolean alreadyUsesExpectedLongWindow(long tokenLimit) {
        if (decision.longContextWindowTokens() <= 0) {
            return false;
        }
        return tokenLimit >= Math.round(decision.longContextWindowTokens() * LONG_WINDOW_MATCH_RATIO);
    }

    private IllegalStateException runtimeAbortFailure(RuntimeException failure) {
        var cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        if (runtimeAbortOutcomePublished.compareAndSet(false, true)) {
            var details = runtimeDetails("RUNTIME_SESSION_ABORT_FAILED", runtimeUpgradeSource);
            details.put("failureType", rootCause(cause).getClass().getSimpleName());
            publish(
                    eventId("runtime-session-abort-failed"),
                    runtimeRequestEventId,
                    "FAILED",
                    "Nie udało się zatrzymać turnu",
                    "Sesja nie może zostać bezpiecznie wznowiona z `long_context`, ponieważ SDK nie potwierdziło abortu.",
                    details
            );
        }
        return new IllegalStateException("Copilot SDK could not abort the active turn for long_context resume.", cause);
    }

    private LinkedHashMap<String, Object> runtimeDetails(String phase, WindowObservation observation) {
        var details = details(phase, "RUNTIME_USAGE_THRESHOLD");
        details.put("runtimeUsageThreshold", decision.runtimeUsageThreshold());
        if (observation != null) {
            addWindowDetails(details, observation);
        }
        return details;
    }

    private LinkedHashMap<String, Object> details(String phase, String trigger) {
        var details = new LinkedHashMap<String, Object>();
        details.put("phase", phase);
        details.put("trigger", trigger);
        details.put("model", decision.modelId());
        details.put("preference", decision.preference().name());
        details.put("requestedTier", CopilotContextTierPolicy.LONG_CONTEXT);
        details.put("estimatedInitialTokens", decision.estimatedInitialTokens());
        if (decision.initialThresholdTokens() > 0) {
            details.put("initialThresholdTokens", decision.initialThresholdTokens());
        }
        if (decision.defaultWindowTokens() > 0) {
            details.put("defaultWindowTokens", decision.defaultWindowTokens());
        }
        if (decision.longContextWindowTokens() > 0) {
            details.put("longContextWindowTokens", decision.longContextWindowTokens());
        }
        details.put("reason", decision.reason());
        return details;
    }

    private void addWindowDetails(Map<String, Object> details, WindowObservation observation) {
        details.put("tokenLimit", observation.tokenLimit());
        details.put("currentTokens", observation.currentTokens());
        details.put("messagesLength", observation.messagesLength());
        details.put("utilizationPercent", observation.utilizationPercent());
    }

    private String initialRequestSummary() {
        if (decision.preference() == CopilotContextTierPreference.LONG_CONTEXT_REQUIRED) {
            return "Platforma ustawiła `long_context` przed otwarciem sesji, ponieważ feature wymaga rozszerzonego okna.";
        }
        return "Platforma ustawiła `long_context` przed otwarciem sesji, ponieważ oszacowany initial context przekroczył skonfigurowany próg.";
    }

    private String initialTrigger() {
        return decision.preference() == CopilotContextTierPreference.LONG_CONTEXT_REQUIRED
                ? "FEATURE_REQUIREMENT"
                : "INITIAL_CONTEXT_THRESHOLD";
    }

    private long thresholdTokens(long tokenLimit) {
        return Math.round(tokenLimit * decision.runtimeUsageThreshold());
    }

    private double utilizationPercent(long currentTokens, long tokenLimit) {
        var ratio = Math.max(currentTokens, 0L) * 100D / tokenLimit;
        return Math.round(ratio * 10D) / 10D;
    }

    private String percentage(double ratio) {
        return Math.round(ratio * 1_000D) / 10D + "%";
    }

    private String eventId(String phase) {
        return "context-tier-" + lifecycleId + "-" + phase;
    }

    private void publish(
            String eventId,
            String parentEventId,
            String status,
            String title,
            String summary,
            Map<String, Object> details
    ) {
        if (activitySink == null) {
            return;
        }
        try {
            activitySink.accept(new AnalysisAiActivityEvent(
                    eventId,
                    parentEventId,
                    EVENT_TYPE,
                    "CONTEXT",
                    status,
                    title,
                    summary,
                    null,
                    null,
                    null,
                    null,
                    Instant.now(),
                    details
            ));
        } catch (RuntimeException failure) {
            log.warn("Copilot context-tier activity listener failed status={} reason={}", status, failure.getMessage());
        }
    }

    private Throwable rootCause(Throwable failure) {
        var current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record WindowObservation(
            long tokenLimit,
            long currentTokens,
            long messagesLength,
            double utilizationPercent
    ) {
    }
}
