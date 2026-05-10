package pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback;

public record CopilotToolFeedbackResult(
        String status,
        String feedbackId,
        String targetToolName,
        String targetToolCallId,
        String resolution
) {
}
