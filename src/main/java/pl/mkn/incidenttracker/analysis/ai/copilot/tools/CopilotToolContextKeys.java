package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

public final class CopilotToolContextKeys {

    public static final String ANALYSIS_RUN_ID = "analysisRunId";
    public static final String COPILOT_SESSION_ID = "copilotSessionId";
    public static final String ACTUAL_COPILOT_SESSION_ID = "actualCopilotSessionId";
    public static final String TOOL_CALL_ID = "toolCallId";
    public static final String TOOL_NAME = "toolName";
    public static final String CORRELATION_ID = "correlationId";
    public static final String ENVIRONMENT = "environment";
    public static final String GITLAB_BRANCH = "gitLabBranch";
    public static final String GITLAB_GROUP = "gitLabGroup";

    private CopilotToolContextKeys() {
    }
}
