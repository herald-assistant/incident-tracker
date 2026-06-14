package pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopilotToolFeedbackInvocationListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCaptureFeedbackAndResolveTargetByToolCallId() throws JsonProcessingException {
        var captured = new ArrayList<AnalysisAiToolFeedback>();
        var store = new CopilotToolEvidenceSessionStore();
        store.registerSession(
                "sdk-session-1",
                section -> captured.addAll(AnalysisAiToolFeedbackEvidenceMapper.fromSection(section))
        );
        var listener = new CopilotToolFeedbackInvocationListener(store, objectMapper);

        listener.onToolInvocationFinished(finished(
                "target-call-1",
                "db_find_tables",
                "{}",
                "{\"status\":\"ok\"}"
        ));

        listener.onToolInvocationFinished(finished(
                "feedback-call-1",
                CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK,
                feedbackArguments(),
                objectMapper.writeValueAsString(new CopilotToolFeedbackResult(
                        "accepted",
                        "feedback-id-1",
                        "db_find_tables",
                        "target-call-1",
                        "event_capture"
                ))
        ));

        assertEquals(1, captured.size());
        assertEquals("feedback-id-1", captured.get(0).feedbackId());
        assertEquals("feedback-call-1", captured.get(0).feedbackToolCallId());
        assertEquals("db_find_tables", captured.get(0).targetToolName());
        assertEquals("target-call-1", captured.get(0).targetToolCallId());
        assertEquals("tool_error", captured.get(0).issueCategory());
    }

    @Test
    void shouldResolveLatestTargetByToolName() throws JsonProcessingException {
        var captured = new ArrayList<AnalysisAiToolFeedback>();
        var store = new CopilotToolEvidenceSessionStore();
        store.registerSession(
                "sdk-session-1",
                section -> captured.addAll(AnalysisAiToolFeedbackEvidenceMapper.fromSection(section))
        );
        var listener = new CopilotToolFeedbackInvocationListener(store, objectMapper);

        listener.onToolInvocationFinished(finished("call-1", "gitlab_find_flow_context", "{}", "{\"status\":\"ok\"}"));
        listener.onToolInvocationFinished(finished("call-2", "db_find_tables", "{}", "{\"status\":\"ok\"}"));
        listener.onToolInvocationFinished(finished("call-3", "gitlab_find_flow_context", "{}", "{\"status\":\"ok\"}"));

        listener.onToolInvocationFinished(finished(
                "feedback-call-1",
                CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK,
                feedbackArguments("gitlab_find_flow_context", "", "partial"),
                objectMapper.writeValueAsString(new CopilotToolFeedbackResult(
                        "accepted",
                        "feedback-id-2",
                        "gitlab_find_flow_context",
                        "",
                        "event_capture"
                ))
        ));

        assertEquals(1, captured.size());
        assertEquals("call-3", captured.get(0).targetToolCallId());
        assertEquals("partial", captured.get(0).usefulness());
    }

    @Test
    void shouldResolveLatestNonFeedbackInvocationWhenTargetIsOmitted() throws JsonProcessingException {
        var captured = new ArrayList<AnalysisAiToolFeedback>();
        var store = new CopilotToolEvidenceSessionStore();
        store.registerSession(
                "sdk-session-1",
                section -> captured.addAll(AnalysisAiToolFeedbackEvidenceMapper.fromSection(section))
        );
        var listener = new CopilotToolFeedbackInvocationListener(store, objectMapper);

        listener.onToolInvocationFinished(finished("call-1", "opctx_search", "{}", "{\"status\":\"ok\"}"));

        listener.onToolInvocationFinished(finished(
                "feedback-call-1",
                CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK,
                feedbackArguments("", "", "not_useful"),
                objectMapper.writeValueAsString(new CopilotToolFeedbackResult(
                        "accepted",
                        "feedback-id-3",
                        "",
                        "",
                        "event_capture"
                ))
        ));

        assertEquals(1, captured.size());
        assertEquals("opctx_search", captured.get(0).targetToolName());
        assertEquals("call-1", captured.get(0).targetToolCallId());
    }

    private String feedbackArguments() throws JsonProcessingException {
        return feedbackArguments("db_find_tables", "target-call-1", "error");
    }

    private String feedbackArguments(
            String targetToolName,
            String targetToolCallId,
            String usefulness
    ) throws JsonProcessingException {
        var arguments = new LinkedHashMap<String, String>();
        arguments.put("targetToolName", targetToolName);
        arguments.put("targetToolCallId", targetToolCallId);
        arguments.put("usefulness", usefulness);
        arguments.put("expectedDataReceived", "no");
        arguments.put("issueCategory", "tool_error");
        arguments.put("improvementArea", "adapter_result");
        arguments.put("confidence", "medium");
        arguments.put("summaryForOperator", "Tool zwrócił błąd zamiast diagnostycznej informacji o tabelach.");
        arguments.put("suggestedImprovement", "Pokazać błąd adaptera w prostszym kontrakcie.");
        return objectMapper.writeValueAsString(arguments);
    }

    private CopilotToolInvocationFinishedEvent finished(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult
    ) {
        return new CopilotToolInvocationFinishedEvent(
                new CopilotToolSessionContext(
                        "run-1",
                        "logical-session-1",
                        "corr-123",
                        "dev3",
                        "main",
                        "CRM/runtime"
                ),
                "sdk-session-1",
                toolCallId,
                toolName,
                rawArguments,
                CopilotToolInvocationOutcome.COMPLETED,
                rawResult,
                12L,
                null
        );
    }
}
