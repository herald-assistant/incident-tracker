package pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore.SessionToolEvidence;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore.ToolInvocationSummary;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CopilotToolFeedbackInvocationListener {

    private static final Set<String> USEFULNESS_VALUES = Set.of(
            "useful",
            "partial",
            "not_useful",
            "invalid",
            "error"
    );
    private static final Set<String> EXPECTED_DATA_VALUES = Set.of(
            "yes",
            "partial",
            "no",
            "unknown"
    );
    private static final Set<String> ISSUE_CATEGORY_VALUES = Set.of(
            "no_issue",
            "no_data",
            "wrong_scope",
            "incomplete",
            "too_much_noise",
            "ambiguous_result",
            "stale_data",
            "access_error",
            "tool_error",
            "schema_or_format_issue",
            "missing_operational_context",
            "missing_code_scope",
            "model_misused_tool",
            "other"
    );
    private static final Set<String> IMPROVEMENT_AREA_VALUES = Set.of(
            "none",
            "tool_contract",
            "tool_description",
            "tool_policy",
            "adapter_result",
            "operational_context_data",
            "code_search_scope",
            "database_mapping",
            "ui_presentation",
            "other"
    );
    private static final Set<String> CONFIDENCE_VALUES = Set.of("low", "medium", "high", "unknown");

    private final CopilotToolEvidenceSessionStore evidenceStore;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onToolInvocationFinished(CopilotToolInvocationFinishedEvent event) {
        if (event == null || !StringUtils.hasText(event.toolName())) {
            return;
        }

        if (CopilotToolFeedbackToolNames.isFeedbackTool(event.toolName())) {
            captureToolFeedback(event);
            return;
        }

        recordInvocation(event);
    }

    private void captureToolFeedback(CopilotToolInvocationFinishedEvent event) {
        if (event.outcome() != CopilotToolInvocationOutcome.COMPLETED
                || !StringUtils.hasText(event.sessionId())
                || !StringUtils.hasText(event.rawArguments())) {
            return;
        }

        evidenceStore.sessionEvidence(event.sessionId()).ifPresent(sessionEvidence -> {
            var request = readRequest(event.rawArguments());
            if (request == null) {
                return;
            }

            var result = readResult(event.rawResult());
            if (result != null && "not_recorded".equals(result.status())) {
                return;
            }
            var feedbackId = result != null && "accepted".equals(result.status()) ? result.feedbackId() : "";
            var feedback = toFeedback(sessionEvidence, event.toolCallId(), feedbackId, request);
            if (feedback == null) {
                return;
            }

            evidenceStore.publishSection(
                    event.sessionId(),
                    event.toolName(),
                    AnalysisAiToolFeedbackEvidenceMapper.toSection(feedback)
            );
        });
    }

    private void recordInvocation(CopilotToolInvocationFinishedEvent event) {
        if (!StringUtils.hasText(event.sessionId()) || !StringUtils.hasText(event.toolName())) {
            return;
        }

        evidenceStore.sessionEvidence(event.sessionId()).ifPresent(sessionEvidence -> sessionEvidence.recordInvocation(
                new ToolInvocationSummary(
                        clean(event.toolName()),
                        clean(event.toolCallId()),
                        event.outcome() != null ? event.outcome().name() : "",
                        event.rawArguments(),
                        event.rawResult(),
                        event.latencyMs()
                )
        ));
    }

    private AnalysisAiToolFeedback toFeedback(
            SessionToolEvidence sessionEvidence,
            String feedbackToolCallId,
            String feedbackId,
            CopilotToolFeedbackRequest request
    ) {
        var target = resolveTarget(sessionEvidence, request);
        var summary = truncate(request != null ? request.summaryForOperator() : "", 1_000);
        if (!StringUtils.hasText(summary)) {
            return null;
        }

        return new AnalysisAiToolFeedback(
                StringUtils.hasText(feedbackId) ? feedbackId : UUID.randomUUID().toString(),
                target != null ? target.toolName() : clean(request != null ? request.targetToolName() : ""),
                target != null ? target.toolCallId() : clean(request != null ? request.targetToolCallId() : ""),
                clean(feedbackToolCallId),
                normalize(request != null ? request.usefulness() : null, USEFULNESS_VALUES, "partial"),
                normalize(request != null ? request.expectedDataReceived() : null, EXPECTED_DATA_VALUES, "unknown"),
                normalize(request != null ? request.issueCategory() : null, ISSUE_CATEGORY_VALUES, "other"),
                normalize(request != null ? request.improvementArea() : null, IMPROVEMENT_AREA_VALUES, "other"),
                normalize(request != null ? request.confidence() : null, CONFIDENCE_VALUES, "medium"),
                summary,
                truncate(request != null ? request.suggestedImprovement() : "", 1_000),
                Instant.now()
        );
    }

    private TargetTool resolveTarget(SessionToolEvidence sessionEvidence, CopilotToolFeedbackRequest request) {
        var targetToolName = clean(request != null ? request.targetToolName() : "");
        var targetToolCallId = clean(request != null ? request.targetToolCallId() : "");

        if (StringUtils.hasText(targetToolCallId)) {
            var byCallId = sessionEvidence.findInvocationByCallId(targetToolCallId);
            if (byCallId.isPresent()) {
                var invocation = byCallId.get();
                return new TargetTool(invocation.toolName(), invocation.toolCallId());
            }
            if (StringUtils.hasText(targetToolName)
                    && !CopilotToolFeedbackToolNames.isFeedbackTool(targetToolName)) {
                return new TargetTool(targetToolName, targetToolCallId);
            }
        }

        if (StringUtils.hasText(targetToolName)
                && !CopilotToolFeedbackToolNames.isFeedbackTool(targetToolName)) {
            var byName = sessionEvidence.findLatestInvocationByName(targetToolName);
            if (byName.isPresent()) {
                var invocation = byName.get();
                return new TargetTool(invocation.toolName(), invocation.toolCallId());
            }
            return new TargetTool(targetToolName, "");
        }

        return sessionEvidence.findLatestInvocation()
                .map(invocation -> new TargetTool(invocation.toolName(), invocation.toolCallId()))
                .orElse(null);
    }

    private CopilotToolFeedbackRequest readRequest(String rawArguments) {
        try {
            return objectMapper.readValue(rawArguments, CopilotToolFeedbackRequest.class);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse tool feedback arguments. reason={}", exception.getMessage());
            return null;
        }
    }

    private CopilotToolFeedbackResult readResult(String rawResult) {
        if (!StringUtils.hasText(rawResult)) {
            return null;
        }

        try {
            return objectMapper.readValue(rawResult, CopilotToolFeedbackResult.class);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse tool feedback result. reason={}", exception.getMessage());
            return null;
        }
    }

    private static String normalize(String value, Set<String> allowed, String fallback) {
        var candidate = clean(value).toLowerCase(Locale.ROOT);
        return allowed.contains(candidate) ? candidate : fallback;
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static String truncate(String value, int maxLength) {
        var cleaned = clean(value);
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    private record TargetTool(
            String toolName,
            String toolCallId
    ) {
    }
}
