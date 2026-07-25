package pl.mkn.tdw.features.changeverification.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;

import java.util.ArrayList;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ChangeVerificationCopilotToolInvocationEvidenceCaptureListener {

    private static final String PROVIDER = "change-verification";
    private static final String CATEGORY = "ai-tool-invocations";
    private static final String ORDER_NAMESPACE = "change-verification-tool-invocation";
    private static final int MAX_CAPTURED_TEXT_LENGTH = 2_500;

    private final CopilotToolEvidenceSessionStore evidenceStore;

    @EventListener
    public void onToolInvocationFinished(CopilotToolInvocationFinishedEvent event) {
        if (!isChangeVerification(event) || !supportedTool(event.toolName()) || !StringUtils.hasText(event.sessionId())) {
            return;
        }

        evidenceStore.sessionEvidence(event.sessionId()).ifPresent(sessionEvidence -> {
            var section = sessionEvidence.appendItem(
                    PROVIDER,
                    CATEGORY,
                    event.toolCallId(),
                    ORDER_NAMESPACE,
                    fallback(event.toolName(), "tool-call"),
                    item(event)
            );
            evidenceStore.publishSection(event.sessionId(), event.toolName(), section);
        });
    }

    private AnalysisEvidenceItem item(CopilotToolInvocationFinishedEvent event) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        attributes.add(attribute("toolName", event.toolName()));
        attributes.add(attribute("toolCallId", event.toolCallId()));
        attributes.add(attribute("outcome", event.outcome() != null ? event.outcome().name() : null));
        attributes.add(attribute("latencyMs", event.latencyMs()));
        attributes.add(attribute("arguments", abbreviate(event.rawArguments())));
        attributes.add(attribute("result", abbreviate(event.rawResult())));
        attributes.add(attribute("error", event.exception() != null ? event.exception().getMessage() : null));

        return new AnalysisEvidenceItem(
                "AI tool: " + fallback(event.toolName(), "unknown") + " -> "
                        + (event.outcome() != null ? event.outcome().name() : "UNKNOWN"),
                attributes
        );
    }

    private boolean isChangeVerification(CopilotToolInvocationFinishedEvent event) {
        if (event == null || event.sessionContext() == null || event.sessionContext().hiddenContext() == null) {
            return false;
        }
        return Objects.equals(
                ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE,
                event.sessionContext().hiddenContext().get(ChangeVerificationCopilotToolContextKeys.FEATURE)
        );
    }

    private boolean supportedTool(String toolName) {
        return StringUtils.hasText(toolName)
                && (toolName.startsWith("gitlab_") || toolName.startsWith("db_"));
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= MAX_CAPTURED_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CAPTURED_TEXT_LENGTH) + "...(" + value.length() + " chars)";
    }

    private String fallback(String primary, String secondary) {
        return StringUtils.hasText(primary) ? primary : secondary;
    }

    private AnalysisEvidenceAttribute attribute(String name, Object value) {
        return new AnalysisEvidenceAttribute(name, value != null ? String.valueOf(value) : "");
    }
}
