package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CopilotToolGuidanceCatalog {

    private static final List<String> DATABASE_REASON_GUIDANCE = List.of(
            "For JPA, repository or data-access symptoms, first ground the entity/repository/table mapping from deterministic GitLab evidence or an enabled GitLab tool call; use DB discovery as fallback only when code grounding is unavailable.",
            "Use code-derived table, column and relation hints instead of guessing names from the exception label.",
            "Always provide reason as one short Polish sentence for the operator."
    );

    private static final Map<String, List<String>> GUIDANCE_BY_TOOL_NAME = Map.ofEntries(
            Map.entry(
                    "gitlab_read_repository_file",
                    List.of(
                            "Expensive. Use only when outline/chunk tools are insufficient.",
                            "Prefer gitlab_read_repository_file_chunk or gitlab_read_repository_file_chunks first.",
                            "Do not use for broad browsing.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    "gitlab_search_repository_candidates",
                    List.of(
                            "Use when project or file is unclear.",
                            "Prefer focused terms from stacktrace, exception, class, entity, repository or service names.",
                            "Use to ground a detailed non-code technical-functional affectedFunction when AFFECTED_FUNCTION_GITLAB_RECOMMENDED is listed.",
                            "Do not use repeatedly with broad generic terms.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    "gitlab_read_repository_file_outline",
                    List.of(
                            "Use before full file reads to understand class role and available method signatures.",
                            "Follow up with focused chunks when a specific method, repository predicate or client call matters.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    "gitlab_read_repository_file_chunk",
                    List.of(
                            "Preferred focused read for a known stack frame, method or predicate.",
                            "Keep line ranges tight and tied to a concrete evidence gap.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    "gitlab_read_repository_file_chunks",
                    List.of(
                            "Use for a small set of directly related files needed to explain the flow.",
                            "Avoid batch browsing unrelated candidates.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    "gitlab_find_flow_context",
                    List.of(
                            "Use when evidence coverage says broader upstream/downstream flow context is missing.",
                            "Use when AFFECTED_FUNCTION_GITLAB_RECOMMENDED is listed to identify the smallest functional flow for affectedFunction.",
                            "Use focused keywords grounded in logs, stacktrace, code evidence or current tool results.",
                            "Use recommended next reads rather than launching broad searches.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    "gitlab_find_class_references",
                    List.of(
                            "Use when a concrete class from stacktrace or code evidence needs ownership or caller context.",
                            "Prefer class names and related hints from existing evidence.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    "db_sample_rows",
                    List.of(
                            "Use only after exact count/group checks and only for minimal technical projections.",
                            "Do not use to browse business data."
                    )
            ),
            Map.entry(
                    "db_join_sample",
                    List.of(
                            "Use only after exact join counts and explicit projected columns are known.",
                            "Do not use to browse business data."
                    )
            ),
            Map.entry(
                    "db_execute_readonly_sql",
                    List.of(
                            "Last resort only.",
                            "Use typed DB tools first.",
                            "May be disabled by runtime policy and budget."
                    )
            )
    );

    public List<String> guidanceFor(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return List.of();
        }

        var normalizedToolName = toolName.trim();
        var guidance = GUIDANCE_BY_TOOL_NAME.getOrDefault(normalizedToolName, List.of());
        if (!normalizedToolName.startsWith("db_")) {
            return guidance;
        }

        var databaseGuidance = new ArrayList<>(guidance);
        databaseGuidance.addAll(DATABASE_REASON_GUIDANCE);
        return List.copyOf(databaseGuidance);
    }
}
