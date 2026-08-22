package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.CopilotSession;
import com.github.copilot.generated.SessionUsageInfoEvent;
import lombok.extern.slf4j.Slf4j;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public final class CopilotContextTierSession {

    private static final String EVENT_TYPE = "platform.context_tier";

    private final CopilotSdkProperties.ContextTierPolicy settings;
    private final CopilotContextTierDecision decision;
    private final String reasoningEffort;
    private final Consumer<AnalysisAiActivityEvent> activitySink;
    private final AtomicBoolean longContextSelected;
    private final AtomicBoolean runtimeSwitchAttempted = new AtomicBoolean();
    private volatile CompletableFuture<Void> runtimeSwitch = CompletableFuture.completedFuture(null);

    CopilotContextTierSession(
            CopilotSdkProperties.ContextTierPolicy settings,
            CopilotContextTierDecision decision,
            String reasoningEffort,
            Consumer<AnalysisAiActivityEvent> activitySink
    ) {
        this.settings = settings;
        this.decision = decision;
        this.reasoningEffort = reasoningEffort;
        this.activitySink = activitySink;
        this.longContextSelected = new AtomicBoolean(decision.useLongContextInitially());
        if (decision.useLongContextInitially()) {
            publish(
                    "COMPLETED",
                    "Rozszerzony kontekst sesji",
                    "Sesja została skonfigurowana z `long_context` przed wysłaniem promptu.",
                    details("INITIAL_PROMPT", decision.estimatedInitialTokens(), decision.initialThresholdTokens())
            );
        } else if (decision.policyEnabled()) {
            var details = details(
                    "INITIAL_PROMPT",
                    decision.estimatedInitialTokens(),
                    decision.initialThresholdTokens()
            );
            details.put("selectedTier", "default");
            publish(
                    "INFO",
                    "Standardowy kontekst sesji",
                    decision.modelSupported()
                            ? "Oszacowany kontekst pozostaje poniżej progu dla `long_context`."
                            : "Dynamiczny katalog Copilota nie potwierdził tieru `long_context`; pozostawiono domyślny tier.",
                    details
            );
        }
    }

    public CopilotContextTierDecision decision() {
        return decision;
    }

    public void onSessionUsage(CopilotSession session, SessionUsageInfoEvent event) {
        if (!decision.policyEnabled() || !decision.modelSupported() || longContextSelected.get()) {
            return;
        }
        var data = event != null ? event.getData() : null;
        if (data == null) {
            return;
        }
        var tokenLimit = numeric(data.tokenLimit());
        var currentTokens = numeric(data.currentTokens());
        if (tokenLimit <= 0D) {
            return;
        }
        if (tokenLimit > decision.defaultWindowTokens()) {
            longContextSelected.set(true);
            return;
        }
        var ratio = currentTokens / tokenLimit;
        if (ratio < settings.getRuntimeUsageThreshold()
                || !runtimeSwitchAttempted.compareAndSet(false, true)) {
            return;
        }

        var eventDetails = details("RUNTIME_USAGE", Math.round(currentTokens), Math.round(tokenLimit));
        eventDetails.put("usageRatio", ratio);
        publish(
                "STARTED",
                "Przełączenie na rozszerzony kontekst",
                "Wykorzystanie bieżącego okna osiągnęło próg polityki platformowej.",
                eventDetails
        );
        try {
            runtimeSwitch = session.setModel(
                            decision.modelId(),
                            reasoningEffort,
                            CopilotContextTierPolicy.LONG_CONTEXT,
                            null
                    )
                    .handle((ignored, failure) -> {
                        if (failure == null) {
                            longContextSelected.set(true);
                            publish(
                                    "COMPLETED",
                                    "Rozszerzony kontekst sesji",
                                    "Copilot potwierdził przełączenie sesji na `long_context`.",
                                    eventDetails
                            );
                        } else {
                            var failureDetails = new LinkedHashMap<>(eventDetails);
                            failureDetails.put("failureType", rootCause(failure).getClass().getSimpleName());
                            publish(
                                    "FAILED",
                                    "Nie udało się rozszerzyć kontekstu",
                                    "Analiza jest kontynuowana z dotychczasowym oknem kontekstowym.",
                                    failureDetails
                            );
                            log.warn(
                                    "Copilot context-tier runtime switch failed model={} reason={}",
                                    decision.modelId(),
                                    rootCause(failure).getMessage()
                            );
                        }
                        return null;
                    });
        } catch (RuntimeException failure) {
            var failureDetails = new LinkedHashMap<>(eventDetails);
            failureDetails.put("failureType", failure.getClass().getSimpleName());
            publish(
                    "FAILED",
                    "Nie udało się rozszerzyć kontekstu",
                    "Analiza jest kontynuowana z dotychczasowym oknem kontekstowym.",
                    failureDetails
            );
            log.warn(
                    "Copilot context-tier runtime switch could not be started model={} reason={}",
                    decision.modelId(),
                    failure.getMessage()
            );
        }
    }

    CompletableFuture<Void> runtimeSwitch() {
        return runtimeSwitch;
    }

    private LinkedHashMap<String, Object> details(String trigger, long observedTokens, long thresholdOrLimit) {
        var details = new LinkedHashMap<String, Object>();
        details.put("trigger", trigger);
        details.put("model", decision.modelId());
        details.put("selectedTier", CopilotContextTierPolicy.LONG_CONTEXT);
        details.put("observedTokens", observedTokens);
        details.put("thresholdOrLimitTokens", thresholdOrLimit);
        details.put("defaultWindowTokens", decision.defaultWindowTokens());
        details.put("longContextWindowTokens", decision.longContextWindowTokens());
        details.put("reason", decision.reason());
        return details;
    }

    private void publish(String status, String title, String summary, Map<String, Object> details) {
        if (activitySink == null) {
            return;
        }
        try {
            activitySink.accept(new AnalysisAiActivityEvent(
                    "context-tier-" + UUID.randomUUID(),
                    null,
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

    private double numeric(Number value) {
        return value != null ? value.doubleValue() : 0D;
    }

    private Throwable rootCause(Throwable failure) {
        var current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
