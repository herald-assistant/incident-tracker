package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

import java.util.List;

@Component
public class CopilotIncidentPromptRenderer {

    public String render(
            InitialAnalysisRequest request,
            CopilotIncidentToolAccessPolicy toolAccessPolicy,
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
        return """
                You are helping with an enterprise software incident analysis.

                The result will be read by an operator, tester, analyst, or junior/mid developer.
                The reader may not know the affected system area. The final result has two
                separate audiences:
                - `functionalAnalysis` is for a business/system analyst who needs to understand
                  where this happens in the system, process and high-level architecture.
                - `technicalAnalysis` is for the technical receiver who must fix, verify or route
                  the incident without re-reading the whole analysis.

                Session context:
                - correlationId: %s
                - environment: %s
                - gitLabBranch: %s
                - gitLabGroup: %s

                Hard rules:
                - Analyze the incident artifacts as the primary source of truth.
                - Read `00-incident-manifest.json` first and use it as the artifact index, then read `01-incident-digest.md`.
                - Use raw evidence artifacts to verify the digest before making a claim.
                - Incident artifacts are embedded directly in this prompt. Do not try to open them from the local filesystem.
                - The authoritative artifact contents are embedded below in this prompt. Treat that embedded content as the full text of the session artifacts.
                - Do not claim that you only see the artifact list when the artifact contents are embedded below.
                - Treat environment, gitLabBranch and gitLabGroup as fixed session context.
                - Only the explicitly listed capability groups are enabled for this session.
                - Local workspace, filesystem and shell or terminal tools are blocked. Do not inspect the local disk.
                - Do not invent environment, branch, group, project, table, owner, process, bounded context, or downstream system.
                - Do not assume facts unsupported by the incident artifacts or tool results.
                - Follow loaded skills for incident analysis, functional analysis, operational context catalog use, GitLab exploration, DB/data diagnostics and technical handoff generation.
                - Use tools only when they can materially confirm, reject, or refine a concrete hypothesis.
                - Use tools only for evidence gaps listed in `evidenceCoverage.gaps` in `00-incident-manifest.json`.
                - Do not use tools just because they are available.
                - GitLab, Elasticsearch, Operational Context and Database tools are coverage-aware and may be enabled for targeted gap filling even when some related evidence is already attached.
                - Operational Context tools provide catalog context for systems, processes, bounded contexts, repositories, integrations, teams, glossary terms and handoff rules. Treat catalog context as grounding and scope guidance, not as standalone proof of incident root cause.
                - Treat `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED` as a targeted gap for `functionalAnalysis`: use attached operational context evidence first; if it is not enough and Operational Context tools are enabled, make a focused catalog lookup before the final answer.
                - Treat `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` as a targeted gap for `technicalAnalysis`: when GitLab tools are enabled, make a focused GitLab exploration attempt before the final answer to ground the technical handoff.
                - If GitLab or Operational Context tools are not enabled, use the attached artifacts and state the visibility limit instead of guessing.
                - `functionalAnalysis` must avoid implementation-first language. Mention code identifiers only when they help anchor the system/process explanation.
                - `technicalAnalysis` must follow Technical Handoff v1 from the loaded `incident-technical-handoff` skill.
                - If `DB_CODE_GROUNDING_NEEDED` is listed, do not start broad DB table/column discovery from guesses.
                - Before the first DB table/column/schema-table query for a JPA, repository or data-access symptom, you must either cite deterministic GitLab evidence that already identifies the entity/repository mapping, call an enabled GitLab tool to try to find that mapping, or state that GitLab grounding is unavailable and use DB discovery as a fallback.
                - Use deterministic GitLab evidence or enabled GitLab tools to identify the entity, repository predicate, likely table/column names and direct relations that should guide DB diagnostics.
                - If an exception, stacktrace or deterministic code evidence grounds a class name, use GitLab class-reference or flow tools with grounded class names and focused keywords before broad DB discovery when that can narrow the affected flow or target tables.
                - Every Elasticsearch HTTP diagnostic tool call, GitLab tool call, Database tool call and Operational Context tool call must include `reason`: one short Polish sentence that explains the practical purpose of this call for the operator. Do not put hidden reasoning or step-by-step chain-of-thought in `reason`.
                - When Elasticsearch HTTP diagnostic tools are enabled and the evidence points to an opaque external/downstream HTTP failure, use path summary first, then fetch one or a few concrete comparison calls only if the summary provides useful candidate paths/statuses.
                - For Elasticsearch HTTP diagnostic tools, prefer grounded path prefixes from logs or user input; do not invent endpoint paths.
                - `elastic_fetch_http_call_logs` uses the current hidden correlationId only when `path` is omitted. When a comparison path is provided, it searches by that path without forcing the current incident correlationId.
                - If visibility is incomplete, state exactly what remains unverified and what the next verification step is.
                %s

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
                jsonResponseContract(),
                formatArtifacts(renderedArtifacts),
                formatEmbeddedArtifacts(renderedArtifacts),
                formatAvailableToolGroups(toolAccessPolicy)
        );
    }

    private String jsonResponseContract() {
        return """
                Return the analysis in Polish.
                Return only valid JSON.
                The final answer must start with `{` and end with `}`.
                Do not wrap it in Markdown.
                Do not add prose before or after JSON.
                Do not add status text such as "I have all the evidence needed" before the JSON.
                Use concise professional Markdown in string values where the schema says markdown string.
                Use `code spans` for technical identifiers such as classes, methods, exceptions, IDs, branches, files, queues, endpoints, or DB objects.
                Use real markdown bullets separated by newline characters inside markdown string values when listing multiple points.
                Never join multiple points with pipe separators like "|".

                Required schema:
                {
                  "detectedProblem": "string",
                  "affectedProcess": "string or nieustalone",
                  "affectedBoundedContext": "string or nieustalone",
                  "affectedTeam": "string or nieustalone",
                  "functionalAnalysis": "markdown string in Polish, Functional Analysis v1",
                  "technicalAnalysis": "markdown string in Polish, Technical Handoff v1",
                  "confidence": "high|medium|low",
                  "visibilityLimits": ["string"]
                }

                `functionalAnalysis` must follow Functional Analysis v1 from the loaded `incident-functional-analysis` skill.
                `technicalAnalysis` must follow Technical Handoff v1 from the loaded `incident-technical-handoff` skill.
                `visibilityLimits` should list the most important unverified assumptions or missing data across both sections.
                """.trim();
    }

    private String formatAvailableToolGroups(CopilotIncidentToolAccessPolicy toolAccessPolicy) {
        var rendered = new StringBuilder();

        if (toolAccessPolicy.elasticToolsEnabled()) {
            rendered.append("- Elasticsearch logs: fetch additional logs only for listed log coverage gaps for the current incident correlationId. For opaque HTTP/downstream failures, compare recent calls by grounded path prefix and fetch concrete sample calls only when that can confirm data, status or request-shape differences. Include a short Polish `reason` in every Elasticsearch HTTP diagnostic tool call.\n");
        }

        if (toolAccessPolicy.gitLabToolsEnabled()) {
            rendered.append("- GitLab code: inspect class references/imports, focused chunks, outlines or flow context only for listed code, flow, technical-analysis or DB code-grounding gaps. Include a short Polish `reason` in every GitLab tool call.\n");
        }

        if (toolAccessPolicy.databaseToolsEnabled()) {
            rendered.append("- Database diagnostics: before table/column discovery, ground table and relation hints from deterministic GitLab evidence or enabled GitLab tools when `DB_CODE_GROUNDING_NEEDED` is listed; use DB discovery as fallback if code grounding is unavailable. Include a short Polish `reason` in every Database tool call.\n");
        }

        if (toolAccessPolicy.operationalContextToolsEnabled()) {
            rendered.append("- Operational context catalog: browse or search systems, repositories, code-search scopes, processes, integrations, bounded contexts, teams, glossary terms and handoff rules only to fill functional-context, ownership, code-scope, DB-targeting or handoff gaps. Include a short Polish `reason` in every Operational Context tool call.\n");
        }

        if (toolAccessPolicy.toolFeedbackEnabled()) {
            rendered.append("- Tool quality feedback: use `record_tool_feedback` only for important tool-result quality signals: especially useful, partial, empty, wrong, misleading, noisy or incorrectly scoped results.\n");
        }

        return rendered.length() > 0
                ? rendered.toString().trim()
                : "- none; rely on the incident artifacts for this session.";
    }

    private String feedbackGuidance(CopilotIncidentToolAccessPolicy toolAccessPolicy) {
        if (!toolAccessPolicy.toolFeedbackEnabled()) {
            return "";
        }

        return """
                - The platform tool `record_tool_feedback` is available for visible tool-quality feedback. Use it only for significant cases: a tool result was especially useful, partial, empty, wrong, misleading, noisy, stale, incorrectly scoped, or suggests a missing tool/policy/operational-context improvement.
                - Feedback is diagnostic for human improvement of tools and operational context. It is not evidence for root cause and must not replace the final analysis.
                """.trim();
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

    private String formatArtifacts(
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
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

        if (rendered.length() == 0) {
            return "- none";
        }

        return rendered.toString();
    }

    private String formatEmbeddedArtifacts(
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
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
}
