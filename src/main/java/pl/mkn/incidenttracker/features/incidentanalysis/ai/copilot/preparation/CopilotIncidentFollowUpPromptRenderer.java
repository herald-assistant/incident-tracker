package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatAnalysisSnapshot;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatTurn;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;

import java.util.List;

@Component
public class CopilotIncidentFollowUpPromptRenderer {

    public String render(
            AnalysisAiChatRequest request,
            CopilotIncidentToolAccessPolicy toolAccessPolicy,
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
                - Every GitLab tool call must include `reason`: one short Polish sentence for the operator.
                - Every Database tool call must include `reason`: one short Polish sentence for the operator.
                - Prefer typed Database tools. Raw SQL is unavailable unless explicitly listed in available tools.
                - If a requested capability is not available because scope was not resolved or the backend did not register it, say that directly and give the best grounded answer from existing evidence.
                - Follow loaded skills for incident analysis, operational context catalog use, GitLab exploration, DB/data diagnostics and technical handoff generation.
                - If the latest user message asks for a handoff, zgloszenie, raport for a developer, QA, DevOps, DBA, data owner, partner system or another team, use the loaded `incident-technical-handoff` skill and answer directly in Markdown using `Technical Handoff v1`.
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
                feedbackGuidance(toolAccessPolicy),
                safeText(request.message()),
                formatAnalysisResult(request.analysisResult()),
                formatHistory(request.history()),
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
                - summary: %s
                - recommendedAction: %s
                - rationale: %s
                - affectedFunction: %s
                - affectedProcess: %s
                - affectedBoundedContext: %s
                - affectedTeam: %s
                """.formatted(
                valueOrUnknown(result.detectedProblem()),
                valueOrUnknown(result.summary()),
                valueOrUnknown(result.recommendedAction()),
                valueOrUnknown(result.rationale()),
                valueOrUnknown(result.affectedFunction()),
                valueOrUnknown(result.affectedProcess()),
                valueOrUnknown(result.affectedBoundedContext()),
                valueOrUnknown(result.affectedTeam())
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
            rendered.append("- Elasticsearch logs: fetch additional logs for the current incident correlationId when the user asks to verify logs or timing.\n");
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
