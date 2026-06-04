package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatAnalysisSnapshot;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatTurn;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigRequest;

import java.util.List;

@Component
public class CopilotIncidentFollowUpPromptRenderer {

    public String render(
            AnalysisAiChatRequest request,
            CopilotIncidentToolAccessPolicy toolAccessPolicy,
            CopilotSessionConfigRequest sessionConfigRequest,
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
        return """
                You are continuing an already completed enterprise software incident analysis.

                The user is now asking a follow-up question or issuing an operator command after the final analysis.
                Answer in Polish. Use clear Markdown unless the user explicitly asks for another text format.

                Session context:
                - correlationId: %s
                - environment: %s
                - gitLabBranch: %s
                - gitLabGroup: %s

                Hard rules:
                - This is a continuation of the existing analysis job, not a new initial analysis run.
                - Use the original incident artifacts, the final analysis result, previously captured tool evidence and the chat history as context.
                - The latest user message is allowed to request targeted verification in GitLab, Elasticsearch or Database tools.
                - Use tools when the latest message asks to check, confirm, verify, inspect, compare, or generate an answer that needs fresh repository, log or database evidence.
                - Do not use tools for broad browsing. Keep each tool call tied to the latest user request.
                - Treat environment, gitLabBranch and gitLabGroup as fixed hidden session scope.
                - GitLab, Elasticsearch and Database tools receive hidden scope from the backend. Do not ask the user to provide correlationId, gitLabGroup, gitLabBranch or environment as tool arguments.
                - Local workspace, filesystem and shell or terminal tools are blocked. Do not inspect the local disk.
                - Every Elasticsearch HTTP diagnostic tool call, GitLab tool call, Database tool call and Operational Context tool call must include `reason`: one short Polish sentence for the operator.
                - When the user asks to compare HTTP calls or inspect opaque downstream/external HTTP failures, use Elasticsearch path summary first, then fetch concrete path/status examples only when they help explain the incident.
                - For Elasticsearch HTTP diagnostics, use grounded paths from logs, user input, or prior tool results. Do not invent endpoint paths.
                - `elastic_fetch_http_call_logs` uses the current hidden correlationId only when `path` is omitted. When a comparison path is provided, it searches by that path without forcing the current incident correlationId.
                - Prefer typed Database tools. Raw SQL is unavailable unless explicitly listed in available tools.
                - If a requested capability is not available because scope was not resolved or the backend did not register it, say that directly and give the best grounded answer from existing evidence.
                %s
                - If the latest user message asks for a handoff, zgloszenie, raport for a developer, QA, DevOps, DBA, data owner, partner system or another team, load `incident-technical-handoff` through the `skill` tool when available and answer directly in Markdown using `Technical Handoff v1`.
                - A technical handoff must keep the required section order, distinguish confirmed facts from hypotheses, and use `Nie ustalono`, `Nie dotyczy`, or `Brak danych w evidence` instead of dropping missing fields.
                - If the user asks for a report, generate the report directly in the requested structure in the chat answer.
                - Do not return a JSON envelope unless the user explicitly asks for JSON.
                - Do not invent facts unsupported by artifacts, prior tool evidence, chat history or new tool results.
                %s

                Latest user message:
                <<<BEGIN LATEST USER MESSAGE>>>
                %s
                <<<END LATEST USER MESSAGE>>>

                Final analysis result:
                %s

                Previous chat history:
                %s

                Runtime skills:
                %s

                Artifacts:
                %s

                Embedded artifact contents:
                %s

                Available capability groups:
                %s
                """.formatted(
                request.correlationId(),
                renderEnvironment(request.environment()),
                renderGitLabBranch(request.gitLabBranch()),
                renderGitLabGroup(request.gitLabGroup()),
                runtimeSkillHardRules(sessionConfigRequest),
                feedbackGuidance(toolAccessPolicy),
                safeText(request.message()),
                formatAnalysisResult(request.analysisResult()),
                formatHistory(request.history()),
                formatRuntimeSkills(sessionConfigRequest),
                formatArtifacts(renderedArtifacts),
                formatEmbeddedArtifacts(renderedArtifacts),
                formatAvailableToolGroups(toolAccessPolicy)
        );
    }

    private String formatAnalysisResult(AnalysisAiChatAnalysisSnapshot result) {
        if (result == null) {
            return "- brak zapisanego wyniku koncowego";
        }

        return """
                - detectedProblem: %s
                - affectedProcess: %s
                - affectedBoundedContext: %s
                - affectedTeam: %s
                - confidence: %s
                - visibilityLimits: %s

                ## functionalAnalysis
                %s

                ## technicalAnalysis
                %s
                """.formatted(
                valueOrUnknown(result.detectedProblem()),
                valueOrUnknown(result.affectedProcess()),
                valueOrUnknown(result.affectedBoundedContext()),
                valueOrUnknown(result.affectedTeam()),
                valueOrUnknown(result.confidence()),
                result.visibilityLimits() == null || result.visibilityLimits().isEmpty()
                        ? "Brak"
                        : String.join("; ", result.visibilityLimits()),
                valueOrUnknown(result.functionalAnalysis()),
                valueOrUnknown(result.technicalAnalysis())
        ).trim();
    }

    private String formatHistory(List<AnalysisAiChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "- brak wczesniejszych wiadomosci";
        }

        var rendered = new StringBuilder();
        for (var turn : history) {
            if (turn == null || turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            if (rendered.length() > 0) {
                rendered.append(System.lineSeparator()).append(System.lineSeparator());
            }
            rendered.append("### ")
                    .append(turn.role() != null && !turn.role().isBlank() ? turn.role() : "message")
                    .append(System.lineSeparator())
                    .append(turn.content().trim());
        }

        return rendered.length() > 0 ? rendered.toString() : "- brak wczesniejszych wiadomosci";
    }

    private String formatAvailableToolGroups(CopilotIncidentToolAccessPolicy toolAccessPolicy) {
        var rendered = new StringBuilder();

        if (toolAccessPolicy.elasticToolsEnabled()) {
            rendered.append("- Elasticsearch logs: fetch additional logs for the current incident correlationId when the user asks to verify logs or timing; compare recent HTTP calls by grounded path prefix and fetch concrete sample calls when the user asks about opaque downstream/external failures. Include a short Polish `reason` in every Elasticsearch HTTP diagnostic tool call.\n");
        }

        if (toolAccessPolicy.gitLabToolsEnabled()) {
            rendered.append("- GitLab code: inspect repository candidates, class references, outlines and focused file chunks in the fixed group and branch. Include a short Polish `reason`.\n");
        }

        if (toolAccessPolicy.databaseToolsEnabled()) {
            rendered.append("- Database diagnostics: use typed readonly diagnostics on the resolved environment. Include a short Polish `reason`.\n");
        }

        if (toolAccessPolicy.operationalContextToolsEnabled()) {
            rendered.append("- Operational context catalog: browse or search reusable catalog context when the latest user request needs ownership, code scope, process, integration, handoff or DB-targeting context. Include a short Polish `reason`.\n");
        }

        if (toolAccessPolicy.toolFeedbackEnabled()) {
            rendered.append("- Tool quality feedback: use `record_tool_feedback` only for important tool-result quality signals: especially useful, partial, empty, wrong, misleading, noisy or incorrectly scoped results.\n");
        }

        return rendered.length() > 0
                ? rendered.toString().trim()
                : "- none; answer only from existing analysis evidence and chat history.";
    }

    private String feedbackGuidance(CopilotIncidentToolAccessPolicy toolAccessPolicy) {
        if (!toolAccessPolicy.toolFeedbackEnabled()) {
            return "";
        }

        return """
                - The platform tool `record_tool_feedback` is available for visible tool-quality feedback. Use it only for significant cases: a tool result was especially useful, partial, empty, wrong, misleading, noisy, stale, incorrectly scoped, or suggests a missing tool/policy/operational-context improvement.
                - Feedback is diagnostic for human improvement of tools and operational context. It is not evidence for root cause and must not replace the chat answer.
                """.trim();
    }

    private String runtimeSkillHardRules(CopilotSessionConfigRequest sessionConfigRequest) {
        if (sessionConfigRequest != null && sessionConfigRequest.skillToolAvailable()) {
            return """
                    - The built-in `skill` tool is enabled for runtime skills. Use it to load available incident runtime skills from `00-incident-manifest.json` under `runtimeSkills.preferredSkillNames` whenever the latest user request depends on skill rules or asks about skill definitions.
                    - Diagnostic-tool restrictions apply to external evidence tools, not to the `skill` tool used for runtime skill loading.
                    - If the user asks what skills are available or where a skill definition came from, answer only after using the `skill` tool; otherwise say the detailed skill contents are not confirmed in this turn.
                    - Do not claim that you know a skill definition, SKILL.md content, or detailed skill rules unless you loaded that skill through the `skill` tool in this session.
                    - Never read skill files from the local filesystem; use only the `skill` tool for runtime skills.
                    """.trim();
        }

        return """
                - The built-in `skill` tool is not enabled for this session. Do not claim detailed runtime skill contents; follow only the embedded artifacts, final analysis, chat history and explicit prompt rules.
                """.trim();
    }

    private String formatRuntimeSkills(CopilotSessionConfigRequest sessionConfigRequest) {
        if (sessionConfigRequest != null && sessionConfigRequest.skillToolAvailable()) {
            return """
                    - built-in tool: `skill`
                    - preferred skills to load when relevant to the latest user request: %s
                    - source: runtime skill directories configured by the backend; do not inspect local paths
                    - if a listed skill is unavailable or disabled, continue with existing evidence and state the limitation only when it affects the answer
                    """.formatted(String.join(", ", CopilotIncidentRuntimeSkillNames.PREFERRED_SKILL_NAMES)).trim();
        }

        return "- built-in `skill` tool is unavailable; no runtime skill contents are confirmed for this session.";
    }

    private String formatArtifacts(List<CopilotRenderedArtifact> renderedArtifacts) {
        var rendered = new StringBuilder();

        for (var artifact : renderedArtifacts) {
            if (rendered.length() > 0) {
                rendered.append(System.lineSeparator());
            }

            rendered.append("- `")
                    .append(artifact.displayName())
                    .append("`: ")
                    .append(artifact.role());

            if (artifact.itemCount() != null) {
                rendered.append(" (items: ").append(artifact.itemCount()).append(")");
            }
        }

        return rendered.length() == 0 ? "- none" : rendered.toString();
    }

    private String formatEmbeddedArtifacts(List<CopilotRenderedArtifact> renderedArtifacts) {
        if (renderedArtifacts.isEmpty()) {
            return "<none>";
        }

        var rendered = new StringBuilder();
        for (var artifact : renderedArtifacts) {
            if (rendered.length() > 0) {
                rendered.append(System.lineSeparator()).append(System.lineSeparator());
            }

            rendered.append("<<<BEGIN ARTIFACT: ")
                    .append(artifact.displayName())
                    .append(" | mimeType=")
                    .append(artifact.mimeType())
                    .append(">>>")
                    .append(System.lineSeparator())
                    .append(artifact.content())
                    .append(System.lineSeparator())
                    .append("<<<END ARTIFACT: ")
                    .append(artifact.displayName())
                    .append(">>>");
        }

        return rendered.toString();
    }

    private String renderGitLabGroup(String gitLabGroup) {
        return gitLabGroup != null && !gitLabGroup.isBlank() ? gitLabGroup : "<not-configured>";
    }

    private String renderEnvironment(String environment) {
        return environment != null && !environment.isBlank() ? environment : "<not-resolved-from-logs>";
    }

    private String renderGitLabBranch(String gitLabBranch) {
        return gitLabBranch != null && !gitLabBranch.isBlank() ? gitLabBranch : "<not-resolved-from-logs>";
    }

    private String valueOrUnknown(String value) {
        return value != null && !value.isBlank() ? value : "nieustalone";
    }

    private String safeText(String value) {
        return value != null ? value.trim() : "";
    }
}
