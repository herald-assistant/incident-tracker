package pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback;

public record CopilotToolFeedbackRequest(
        String targetToolName,
        String targetToolCallId,
        String usefulness,
        String expectedDataReceived,
        String issueCategory,
        String improvementArea,
        String confidence,
        String summaryForOperator,
        String suggestedImprovement
) {
}
