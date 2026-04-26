package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequestResult;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import com.github.copilot.sdk.json.PreToolUseHookOutput;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SessionHooks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CopilotSdkPreparationService {

    private final CopilotSdkProperties properties;
    private final CopilotSdkToolBridge toolBridge;
    private final CopilotSkillRuntimeLoader skillRuntimeLoader;
    private final CopilotArtifactService artifactService;
    private final CopilotSessionMetricsRegistry metricsRegistry;

    public CopilotSdkPreparedRequest prepare(AnalysisAiAnalysisRequest request) {
        var preparationStart = System.nanoTime();
        var toolSessionContext = buildToolSessionContext(request);
        var registeredTools = toolBridge.buildToolDefinitions(toolSessionContext);
        var toolAccessPolicy = CopilotToolAccessPolicy.from(request, registeredTools);
        var tools = toolAccessPolicy.enabledTools();
        var renderedArtifacts = artifactService.renderArtifacts(request, toolAccessPolicy);
        var skillDirectories = skillRuntimeLoader.resolveSkillDirectories();

        var clientOptions = new CopilotClientOptions()
                .setUseLoggedInUser(true)
                .setCwd(properties.getWorkingDirectory());

        if (properties.getCliPath() != null && !properties.getCliPath().isBlank()) {
            clientOptions.setCliPath(properties.getCliPath());
        }

        if (properties.getGithubToken() != null && !properties.getGithubToken().isBlank()) {
            clientOptions
                    .setGitHubToken(properties.getGithubToken())
                    .setUseLoggedInUser(false);
        }

        var sessionConfig = new SessionConfig()
                .setSessionId(toolSessionContext.copilotSessionId())
                .setClientName(properties.getClientName())
                .setWorkingDirectory(properties.getWorkingDirectory())
                .setStreaming(false)
                .setTools(tools)
                .setAvailableTools(toolAccessPolicy.availableToolNames())
                .setSkillDirectories(skillDirectories)
                .setHooks(toolAccessHooks(toolAccessPolicy))
                .setOnPermissionRequest(permissionHandler())
                .setDisabledSkills(safeList(properties.getDisabledSkills()));

        if (properties.getModel() != null && !properties.getModel().isBlank()) {
            sessionConfig.setModel(properties.getModel());
        }

        if (properties.getReasoningEffort() != null && !properties.getReasoningEffort().isBlank()) {
            sessionConfig.setReasoningEffort(properties.getReasoningEffort());
        }

        String prompt = buildPrompt(request, toolAccessPolicy, renderedArtifacts);
        var artifactContents = artifactService.toArtifactContentMap(renderedArtifacts);
        var messageOptions = new MessageOptions().setPrompt(prompt);
        metricsRegistry.recordPreparation(
                toolSessionContext,
                request,
                renderedArtifacts,
                prompt,
                (System.nanoTime() - preparationStart) / 1_000_000
        );

        return new CopilotSdkPreparedRequest(
                request.correlationId(),
                clientOptions,
                sessionConfig,
                messageOptions,
                prompt,
                artifactContents
        );
    }

    public String preparePrompt(AnalysisAiAnalysisRequest request) {
        var tools = toolBridge.buildToolDefinitions(buildToolSessionContext(request));
        var toolAccessPolicy = CopilotToolAccessPolicy.from(request, tools);
        var renderedArtifacts = artifactService.renderArtifacts(request, toolAccessPolicy);
        return buildPrompt(request, toolAccessPolicy, renderedArtifacts);
    }

    private CopilotToolSessionContext buildToolSessionContext(AnalysisAiAnalysisRequest request) {
        var analysisRunId = UUID.randomUUID().toString();
        var copilotSessionId = "analysis-" + analysisRunId;

        return new CopilotToolSessionContext(
                analysisRunId,
                copilotSessionId,
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup()
        );
    }

    private String buildPrompt(
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
                - Read `00-incident-manifest.json` first and use it as the artifact index.
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
                - GitLab and Elasticsearch tools are fallback-only and are enabled only when the corresponding artifact data is missing.
                - If the incident artifacts already contain enough evidence and the affected flow is understandable, answer directly.
                - If the likely technical error is clear but the affected function or broader flow is not understandable for a beginner analyst, use GitLab tools to read enough surrounding code to explain the flow and handoff.
                - If a JPA, repository or data-access symptom is suspected, first use deterministic GitLab evidence or enabled GitLab tools to identify the entity, repository predicate, likely table/column names and direct relations that should guide DB diagnostics.
                - If an exception, stacktrace or deterministic code evidence grounds a class name, use GitLab search for that class and its imports/references before broad DB discovery when that can narrow the affected flow or target tables.
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

                `evidenceReferences` may be an empty array when stable item IDs are unavailable.
                `visibilityLimits` should list the most important unverified assumptions or missing data.
                """.trim();
    }

    private String formatAvailableToolGroups(CopilotToolAccessPolicy toolAccessPolicy) {
        var rendered = new StringBuilder();

        if (toolAccessPolicy.elasticToolsEnabled()) {
            rendered.append("- Elasticsearch logs: fetch additional logs for the current incident correlationId.\n");
        }

        if (toolAccessPolicy.gitLabToolsEnabled()) {
            rendered.append("- GitLab code: search broadly across relevant repositories, inspect class references/imports, and read focused chunks/files to explain the failing code path, repository predicates, JPA/table hints, integrations and affected functional flow.\n");
        }

        if (toolAccessPolicy.databaseToolsEnabled()) {
            rendered.append("- Database diagnostics: verify data-dependent hypotheses by inspecting DB scope, finding/describing tables, and using code-derived entity/repository/table hints before exact counts, predicate checks, reference checks, process states or minimal samples.\n");
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

    private PermissionHandler permissionHandler() {
        return switch (properties.getPermissionMode()) {
            case DENY_ALL -> (request, invocation) -> CompletableFuture.completedFuture(
                    new PermissionRequestResult().setKind(PermissionRequestResultKind.DENIED_BY_RULES)
            );
            case APPROVE_ALL -> PermissionHandler.APPROVE_ALL;
        };
    }

    private SessionHooks toolAccessHooks(CopilotToolAccessPolicy toolAccessPolicy) {
        return new SessionHooks().setOnPreToolUse((input, invocation) -> {
            var toolName = input != null ? input.getToolName() : null;
            if (toolName != null && toolAccessPolicy.availableToolNames().contains(toolName)) {
                return CompletableFuture.completedFuture(PreToolUseHookOutput.allow());
            }

            return CompletableFuture.completedFuture(PreToolUseHookOutput.deny(
                    "Use only the inline incident artifacts and the explicitly enabled incident-analysis tools for this session."
            ));
        });
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }
}
