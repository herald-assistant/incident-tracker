package pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback;

public final class CopilotToolFeedbackToolNames {

    public static final String RECORD_TOOL_FEEDBACK = "record_tool_feedback";

    private CopilotToolFeedbackToolNames() {
    }

    public static boolean isFeedbackTool(String toolName) {
        return RECORD_TOOL_FEEDBACK.equals(toolName);
    }
}
