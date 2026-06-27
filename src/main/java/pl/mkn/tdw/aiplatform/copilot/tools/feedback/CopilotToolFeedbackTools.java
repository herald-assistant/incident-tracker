package pl.mkn.tdw.aiplatform.copilot.tools.feedback;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class CopilotToolFeedbackTools {

    private static final String USEFULNESS_DESCRIPTION =
            "One of: useful, partial, not_useful, invalid, error.";
    private static final String EXPECTED_DATA_DESCRIPTION =
            "Whether the tool returned the data expected by the model. One of: yes, partial, no, unknown.";
    private static final String ISSUE_CATEGORY_DESCRIPTION =
            "One of: no_issue, no_data, wrong_scope, incomplete, too_much_noise, ambiguous_result, stale_data, "
                    + "access_error, tool_error, schema_or_format_issue, missing_operational_context, "
                    + "missing_code_scope, model_misused_tool, other.";
    private static final String IMPROVEMENT_AREA_DESCRIPTION =
            "One of: none, tool_contract, tool_description, tool_policy, adapter_result, operational_context_data, "
                    + "code_search_scope, database_mapping, ui_presentation, other.";

    @Tool(
            name = CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK,
            description = """
                    Records user-visible quality feedback about a previous tool result in the current AI session.
                    Use only for important cases: especially useful, partial, empty, wrong, misleading, noisy,
                    incorrectly scoped, or otherwise improvable tool results. Do not use it for routine successful
                    tool calls. The analysis scope is taken from the current session; do not provide analysisId,
                    correlationId, environment, gitLabGroup or gitLabBranch.
                    """
    )
    public CopilotToolFeedbackResult recordToolFeedback(
            @ToolParam(required = false, description = "Name of the tool being evaluated. Prefer the exact tool name from the recent tool call.")
            String targetToolName,
            @ToolParam(required = false, description = "Optional toolCallId of the specific previous tool call being evaluated.")
            String targetToolCallId,
            @ToolParam(description = USEFULNESS_DESCRIPTION)
            String usefulness,
            @ToolParam(description = EXPECTED_DATA_DESCRIPTION)
            String expectedDataReceived,
            @ToolParam(description = ISSUE_CATEGORY_DESCRIPTION)
            String issueCategory,
            @ToolParam(description = IMPROVEMENT_AREA_DESCRIPTION)
            String improvementArea,
            @ToolParam(description = "Confidence in this feedback: low, medium, high or unknown.")
            String confidence,
            @ToolParam(description = "Short Polish summary for the operator explaining what was good or wrong with the tool result.")
            String summaryForOperator,
            @ToolParam(required = false, description = "Optional Polish suggestion for improving the tool, policy, operational context or UI.")
            String suggestedImprovement
    ) {
        if (!StringUtils.hasText(summaryForOperator)) {
            return new CopilotToolFeedbackResult(
                    "not_recorded",
                    "",
                    cleanTargetToolName(targetToolName),
                    clean(targetToolCallId),
                    "missing_summary"
            );
        }

        return new CopilotToolFeedbackResult(
                "accepted",
                UUID.randomUUID().toString(),
                cleanTargetToolName(targetToolName),
                clean(targetToolCallId),
                "event_capture"
        );
    }

    private String cleanTargetToolName(String value) {
        var toolName = clean(value);
        return CopilotToolFeedbackToolNames.isFeedbackTool(toolName) ? "" : toolName;
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }
}
