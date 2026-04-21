package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequestResult;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CopilotSdkPreparationService {

    private final CopilotSdkProperties properties;
    private final CopilotSdkToolBridge toolBridge;
    private final CopilotSkillRuntimeLoader skillRuntimeLoader;
    private final CopilotAttachmentArtifactService attachmentArtifactService;

    public CopilotSdkPreparedRequest prepare(AnalysisAiAnalysisRequest request) {
        var tools = toolBridge.buildToolDefinitions();
        var artifactDescriptors = attachmentArtifactService.describe(request);
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
                .setClientName(properties.getClientName())
                .setWorkingDirectory(properties.getWorkingDirectory())
                .setStreaming(false)
                .setTools(tools)
                .setSkillDirectories(skillDirectories)
                .setOnPermissionRequest(permissionHandler())
                .setDisabledSkills(safeList(properties.getDisabledSkills()));

        if (properties.getModel() != null && !properties.getModel().isBlank()) {
            sessionConfig.setModel(properties.getModel());
        }

        if (properties.getReasoningEffort() != null && !properties.getReasoningEffort().isBlank()) {
            sessionConfig.setReasoningEffort(properties.getReasoningEffort());
        }

        String prompt = buildPrompt(request, tools, artifactDescriptors);
        var attachmentArtifacts = attachmentArtifactService.create(request);

        try {
            var messageOptions = new MessageOptions()
                    .setPrompt(prompt)
                    .setAttachments(attachmentArtifacts.attachments());

            return new CopilotSdkPreparedRequest(
                    request.correlationId(),
                    clientOptions,
                    sessionConfig,
                    messageOptions,
                    prompt,
                    attachmentArtifacts
            );
        } catch (RuntimeException exception) {
            attachmentArtifacts.close();
            throw exception;
        }
    }

    public String preparePrompt(AnalysisAiAnalysisRequest request) {
        var tools = toolBridge.buildToolDefinitions();
        var artifactDescriptors = attachmentArtifactService.describe(request);
        return buildPrompt(request, tools, artifactDescriptors);
    }

    private String buildPrompt(
            AnalysisAiAnalysisRequest request,
            List<ToolDefinition> tools,
            List<CopilotAttachmentArtifactService.AttachmentArtifactDescriptor> artifactDescriptors
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
                - Analyze the attached artifacts as the primary source of truth.
                - Read `00-incident-manifest.json` first and use it as the attachment index.
                - Treat environment, gitLabBranch and gitLabGroup as fixed session context.
                - Do not invent environment, branch, group, project, table, owner, process, bounded context, or downstream system.
                - Do not assume facts unsupported by attached artifacts or tool results.
                - Follow loaded skills for incident analysis, GitLab exploration, DB/data diagnostics and handoff quality.
                - Use tools only when they can materially confirm, reject, or refine a concrete hypothesis.
                - If attached artifacts already contain enough evidence and the affected flow is understandable, answer directly.
                - If the likely technical error is clear but the affected function or broader flow is not understandable for a beginner analyst, use GitLab tools to read enough surrounding code to explain the flow and handoff.
                - If visibility is incomplete, state exactly what remains unverified and what the next verification step is.

                Return the analysis in Polish.
                Keep field names exactly as shown below.
                Use concise professional Markdown in field values.
                Use `code spans` for technical identifiers such as classes, methods, exceptions, IDs, branches, files, queues, endpoints, or DB objects.
                Use real markdown bullets on separate lines when listing multiple points.
                Never join multiple points with pipe separators like "|".

                Return exactly these lines:
                detectedProblem: <one precise short technical label scoped as narrowly as evidence allows; plain text>
                summary: <short markdown block: one opening sentence + 2-5 bullets covering strongest evidence, where the failure happens, likely failure domain, and visibility limits>
                recommendedAction: <2-4 prioritized markdown bullets saying who should act next and what should be verified or changed>
                rationale: <3-7 markdown bullets separating confirmed evidence, best-supported hypothesis, surrounding flow, and visibility limits>
                affectedFunction: <short markdown block: one opening sentence + 2-5 bullets explaining the affected function, its role in the broader flow, key collaborators, and where the incident interrupts it>
                affectedProcess: <short Polish plain-text label grounded in operational-context evidence when available; write `nieustalone` if not grounded>
                affectedBoundedContext: <short Polish plain-text label grounded in operational-context evidence when available; write `nieustalone` if not grounded>
                affectedTeam: <short Polish plain-text label for the team that should own or receive the handoff, grounded in operational-context evidence when available; write `nieustalone` if not grounded>

                Attached artifacts:
                %s

                Available capability groups:
                %s
                """.formatted(
                request.correlationId(),
                renderEnvironment(request.environment()),
                renderGitLabBranch(request.gitLabBranch()),
                renderGitLabGroup(request.gitLabGroup()),
                formatAttachedArtifacts(artifactDescriptors),
                formatAvailableToolGroups(tools)
        );
    }

    private String formatAvailableToolGroups(Iterable<ToolDefinition> tools) {
        boolean elastic = false;
        boolean gitlab = false;
        boolean database = false;

        for (var tool : tools) {
            var name = tool.name();

            if (name.startsWith("elastic_")) {
                elastic = true;
            } else if (name.startsWith("gitlab_")) {
                gitlab = true;
            } else if (name.startsWith("db_")) {
                database = true;
            }
        }

        var rendered = new StringBuilder();

        if (elastic) {
            rendered.append("- Elasticsearch logs: fetch additional logs for the current incident correlationId.\n");
        }

        if (gitlab) {
            rendered.append("- GitLab code: search broadly across relevant repositories and read focused chunks/files to explain the failing code path, repository predicates, integrations and affected functional flow.\n");
        }

        if (database) {
            rendered.append("- Database diagnostics: verify data-dependent hypotheses by inspecting DB scope, finding/describing tables, counting rows, checking predicates, references, process states and minimal samples.\n");
        }

        return rendered.length() > 0 ? rendered.toString().trim() : "- none";
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

    private String formatAttachedArtifacts(
            List<CopilotAttachmentArtifactService.AttachmentArtifactDescriptor> artifactDescriptors
    ) {
        var rendered = new StringBuilder();

        for (var artifact : artifactDescriptors) {
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

    private PermissionHandler permissionHandler() {
        return switch (properties.getPermissionMode()) {
            case DENY_ALL -> (request, invocation) -> CompletableFuture.completedFuture(
                    new PermissionRequestResult().setKind(PermissionRequestResultKind.DENIED_BY_RULES)
            );
            case APPROVE_ALL -> PermissionHandler.APPROVE_ALL;
        };
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }
}
