package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;

import java.util.List;

@Component
public class CopilotPromptRenderer {

    public String render(
            AnalysisAiAnalysisRequest request,
            CopilotToolAccessPolicy toolAccessPolicy,
            List<CopilotArtifactService.Artifact> renderedArtifacts
    ) {
        return """
                You are helping with an enterprise software incident analysis.

                The result will be read by an operator, tester, analyst, or junior/mid developer.
                The reader may not know the affected system area.
                Your job is to explain not only the likely error, but also the affected function,
                where the incident interrupts the broader flow, what is confirmed, what remains
                uncertain, and what should be done next.

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
                - Follow loaded skills for incident analysis, GitLab exploration, DB/data diagnostics and handoff quality.
                - Use tools only when they can materially confirm, reject, or refine a concrete hypothesis.
                - Use tools only for evidence gaps listed in `evidenceCoverage.gaps` in `00-incident-manifest.json`.
                - Do not use tools just because they are available.
                - GitLab, Elasticsearch and Database tools are coverage-aware and may be enabled for targeted gap filling even when some related evidence is already attached.
                - Treat `AFFECTED_FUNCTION_GITLAB_RECOMMENDED` as a targeted gap: when GitLab tools are enabled, make a focused GitLab exploration attempt before the final answer to ground `affectedFunction`.
                - If GitLab tools are not enabled or the GitLab attempt cannot find useful flow context, use the attached artifacts and state the visibility limit instead of guessing.
                - If the incident artifacts already contain enough evidence but GitLab tools are enabled, still use a focused GitLab search/read to improve `affectedFunction`; if GitLab tools are not enabled, answer directly from artifacts.
                - If the likely technical error is clear but the affected function or broader flow is not understandable for a beginner analyst, use GitLab tools to read enough surrounding code to explain the flow and handoff.
                - Write `affectedFunction` in non-code, operator-friendly technical/functional language: explain the capability, trigger, main participants, data/object being handled, and where the incident interrupts the flow.
                - If `DB_CODE_GROUNDING_NEEDED` is listed, do not start broad DB table/column discovery from guesses.
                - Before the first DB table/column/schema-table query for a JPA, repository or data-access symptom, you must either cite deterministic GitLab evidence that already identifies the entity/repository mapping, call an enabled GitLab tool to try to find that mapping, or state that GitLab grounding is unavailable and use DB discovery as a fallback.
                - Use deterministic GitLab evidence or enabled GitLab tools to identify the entity, repository predicate, likely table/column names and direct relations that should guide DB diagnostics.
                - If an exception, stacktrace or deterministic code evidence grounds a class name, use GitLab class-reference or flow tools with grounded class names and focused keywords before broad DB discovery when that can narrow the affected flow or target tables.
                - Every GitLab tool call must include `reason`: one short Polish sentence that explains the practical purpose of this read/search for the operator. Do not put hidden reasoning or step-by-step chain-of-thought in `reason`.
                - Every Database tool call must include `reason`: one short Polish sentence that explains why this DB result is useful for the operator. Do not put hidden reasoning or step-by-step chain-of-thought in `reason`.
                - When possible, include evidenceReferences with artifactId and itemId for important claims.
                - If visibility is incomplete, state exactly what remains unverified and what the next verification step is.

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
                Do not wrap it in Markdown.
                Do not add prose before or after JSON.
                Use concise professional Markdown in string values where the schema says markdown string.
                Use `code spans` for technical identifiers such as classes, methods, exceptions, IDs, branches, files, queues, endpoints, or DB objects.
                Use real markdown bullets separated by newline characters inside markdown string values when listing multiple points.
                Never join multiple points with pipe separators like "|".

                Required schema:
                {
                  "detectedProblem": "string",
                  "summary": "markdown string in Polish",
                  "recommendedAction": "markdown string in Polish",
                  "rationale": "markdown string in Polish",
                  "affectedFunction": "markdown string in Polish",
                  "affectedProcess": "string or nieustalone",
                  "affectedBoundedContext": "string or nieustalone",
                  "affectedTeam": "string or nieustalone",
                  "confidence": "high|medium|low",
                  "evidenceReferences": [
                    {
                      "field": "detectedProblem|summary|recommendedAction|rationale|affectedFunction|affectedProcess|affectedBoundedContext|affectedTeam",
                      "artifactId": "string",
                      "itemId": "string",
                      "claim": "short Polish explanation"
                    }
                  ],
                  "visibilityLimits": ["string"]
                }

                Prefer `evidenceReferences` for important claims and use the artifact display name as `artifactId`.
                Use stable `itemId` values from the evidence artifacts whenever a claim is grounded in a specific evidence item.
                `evidenceReferences` may be an empty array only when a claim cannot be tied to a specific artifact item.
                `affectedFunction` must be detailed, non-code, technical/functional Polish. Mention code identifiers only as supporting details, not as the whole explanation.
                `visibilityLimits` should list the most important unverified assumptions or missing data.
                """.trim();
    }

    private String formatAvailableToolGroups(CopilotToolAccessPolicy toolAccessPolicy) {
        var rendered = new StringBuilder();

        if (toolAccessPolicy.elasticToolsEnabled()) {
            rendered.append("- Elasticsearch logs: fetch additional logs only for listed log coverage gaps for the current incident correlationId.\n");
        }

        if (toolAccessPolicy.gitLabToolsEnabled()) {
            rendered.append("- GitLab code: inspect class references/imports, focused chunks, outlines or flow context only for listed code, flow, affected-function or DB code-grounding gaps. Include a short Polish `reason` in every GitLab tool call.\n");
        }

        if (toolAccessPolicy.databaseToolsEnabled()) {
            rendered.append("- Database diagnostics: before table/column discovery, ground table and relation hints from deterministic GitLab evidence or enabled GitLab tools when `DB_CODE_GROUNDING_NEEDED` is listed; use DB discovery as fallback if code grounding is unavailable. Include a short Polish `reason` in every Database tool call.\n");
        }

        return rendered.length() > 0
                ? rendered.toString().trim()
                : "- none; rely on the incident artifacts for this session.";
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
            List<CopilotArtifactService.Artifact> renderedArtifacts
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
            List<CopilotArtifactService.Artifact> renderedArtifacts
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
