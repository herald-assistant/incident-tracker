package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.CopilotSession;
import lombok.extern.slf4j.Slf4j;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
public final class CopilotContextTierSession {

    private static final String EVENT_TYPE = "platform.context_tier";

    private final CopilotContextTierDecision decision;
    private final Consumer<AnalysisAiActivityEvent> activitySink;
    private final CopilotEffectiveContextTierReader effectiveContextTierReader;

    CopilotContextTierSession(
            CopilotContextTierDecision decision,
            Consumer<AnalysisAiActivityEvent> activitySink,
            CopilotEffectiveContextTierReader effectiveContextTierReader
    ) {
        this.decision = decision;
        this.activitySink = activitySink;
        this.effectiveContextTierReader = effectiveContextTierReader;
        if (decision.useLongContextInitially()) {
            publish(
                    "STARTED",
                    "Weryfikacja rozszerzonego kontekstu",
                    "Sesja zostanie otwarta z wymaganym `long_context`, a efektywny tier zostanie sprawdzony przed wysłaniem promptu.",
                    details("PRE_SESSION", decision.estimatedInitialTokens(), decision.initialThresholdTokens())
            );
        } else if (decision.policyEnabled()) {
            var details = details(
                    "INITIAL_PROMPT",
                    decision.estimatedInitialTokens(),
                    decision.initialThresholdTokens()
            );
            details.put("requestedTier", "default");
            publish(
                    "INFO",
                    "Standardowy kontekst sesji",
                    decision.modelMetadataAvailable()
                            ? "Oszacowany kontekst pozostaje poniżej progu dla `long_context`."
                            : "Dynamiczny katalog Copilota nie potwierdził tieru `long_context`; pozostawiono domyślny tier.",
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

        CopilotEffectiveContextTier effectiveTier;
        try {
            effectiveTier = effectiveContextTierReader.read(session);
        } catch (RuntimeException failure) {
            var details = details("SDK_MODEL_GET_CURRENT", decision.estimatedInitialTokens(), 0);
            details.put("failureType", rootCause(failure).getClass().getSimpleName());
            publish(
                    "FAILED",
                    "Nie potwierdzono rozszerzonego kontekstu",
                    "Prompt nie został wysłany, ponieważ SDK nie potwierdziło efektywnego tieru sesji.",
                    details
            );
            throw new IllegalStateException(
                    "Copilot SDK could not verify long_context before the first message.",
                    failure
            );
        }

        var details = details("SDK_MODEL_GET_CURRENT", decision.estimatedInitialTokens(), 0);
        details.put("effectiveTier", effectiveTier.contextTier());
        details.put("effectiveModel", effectiveTier.modelId());
        details.put("effectiveReasoningEffort", effectiveTier.reasoningEffort());
        if (!CopilotContextTierPolicy.LONG_CONTEXT.equals(effectiveTier.contextTier())) {
            publish(
                    "FAILED",
                    "Rozszerzony kontekst nie jest aktywny",
                    "Prompt nie został wysłany, ponieważ efektywny tier sesji nie jest równy `long_context`.",
                    details
            );
            throw new IllegalStateException(
                    "Copilot SDK did not activate long_context before the first message; effective tier: "
                            + effectiveTier.contextTier()
            );
        }

        publish(
                "COMPLETED",
                "Rozszerzony kontekst sesji",
                "Copilot SDK potwierdził `long_context` przed wysłaniem promptu.",
                details
        );
    }

    private LinkedHashMap<String, Object> details(String trigger, long estimatedTokens, long thresholdTokens) {
        var details = new LinkedHashMap<String, Object>();
        details.put("trigger", trigger);
        details.put("model", decision.modelId());
        details.put("preference", decision.preference().name());
        details.put("requestedTier", CopilotContextTierPolicy.LONG_CONTEXT);
        details.put("estimatedInitialTokens", estimatedTokens);
        if (thresholdTokens > 0) {
            details.put("initialThresholdTokens", thresholdTokens);
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

    private Throwable rootCause(Throwable failure) {
        var current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
