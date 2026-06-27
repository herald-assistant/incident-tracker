package pl.mkn.tdw.aiplatform.copilot.tools.feedback;

public record CopilotToolFeedbackResult(
        String status,
        String feedbackId,
        String targetToolName,
        String targetToolCallId,
        String resolution
) {
}
