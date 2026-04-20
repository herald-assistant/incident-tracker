package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequestResult;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.ToolDefinition;
import lombok.RequiredArgsConstructor;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;

import java.util.concurrent.CompletableFuture;
import java.util.List;

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
                Your answer will be read by an analyst, tester, or junior/mid developer who may need to triage the incident,
                continue local diagnosis, or hand the case over to another Tribe, admins, integration owners, or DB specialists.
                The reader may be new to the affected area and may not know the broader functional flow behind the failing capability.
                Write a result that is precise, grounded, operationally useful, and safe for handoff.

                correlationId: %s
                environment: %s
                gitLabBranch: %s
                gitLabGroup: %s

                Analyze the attached artifacts as the primary source of truth.
                Read `00-incident-manifest.json` first and use it as the attachment index.
                Do not assume facts that are not supported by the attached artifacts or tool results.
                Follow any loaded skills that are relevant for incident analysis and tool usage.
                When using GitLab tools, keep the provided gitLabGroup and gitLabBranch unchanged.
                Treat Dynatrace evidence as initial runtime context only. No Dynatrace tools are available during the session.
                If gitLabBranch is not resolved from logs, do not invent one.
                Infer project names and file paths only from evidence and repository exploration.
                If the attached artifacts already contain a concrete exception stacktrace and matching GitLab code around the failing line, answer directly instead of calling more tools.
                When GitLab exploration is needed, do not stop at the single failing line if understanding the surrounding functional or technical flow would materially improve the diagnosis, handoff, or next step.
                If one focused code read is not enough for a newcomer to understand the incident, expand the exploration to the surrounding method, class, service flow, and a few directly collaborating files or integration points.
                Prefer to explain where in the broader request or business flow the failure happens, what leads into that point, what happens immediately after it, and which downstream systems or components are involved.
                The available evidence comes only from our system: Elasticsearch logs, Dynatrace runtime signals, and GitLab code context.
                The real cause may still be in an external integration, downstream service, platform configuration, database state, messaging layer,
                infrastructure, or an area owned by another team that is not directly visible here.
                If the likely problem is outside our codebase or outside the telemetry currently available, say that explicitly.
                If `operational-context` evidence contains matched processes, bounded contexts, teams, or handoff rules, use them to ground ownership and handoff decisions.
                Do not name a specific process, bounded context, or team unless it is supported by matched operational-context evidence or by very strong corroborating runtime/code evidence.
                If ownership is still ambiguous, write `nieustalone` for the affected process, bounded context, or team instead of guessing.
                If you recommend a handoff, keep it aligned with the grounded `affectedTeam` value and explain briefly which operational-context evidence supports that owner.
                Separate clearly:
                - what is directly confirmed by the evidence,
                - what is the best-supported hypothesis,
                - what remains unverified or outside current visibility,
                - what the broader functional context is for someone new to this area,
                - what the fastest next verification or escalation step should be.
                Do not overclaim root cause in systems that are not directly visible in the evidence.
                Avoid generic advice like "check logs" or "investigate further" unless you say exactly what should be verified and why.
                Prefer concrete next steps that help a mid-level technical user act effectively.
                If another team, admins, integration owners, or DB specialists are likely needed, say so directly and explain what should be handed off.
                Return the analysis in Polish.
                Keep the field names exactly as shown below, but write every field value in concise, professional Polish.
                Preserve technical identifiers such as class names, method names, exception types, branch names and file paths in their original form.
                Use valid Markdown in summary, affectedFunction, recommendedAction, and rationale.
                Use real markdown bullets on separate lines instead of pseudo-separators.
                Never use pipe separators like "|" to join multiple points in one line.
                Put every affectedFunction, recommendation, or rationale point on its own line starting with "- " when listing multiple points.
                Use **bold** for the most important facts and `code spans` for technical identifiers such as classes, methods, exceptions, endpoints, IDs, CIF values, branch names, projects, metrics, queues, or DB objects.
                You may use a short "---" separator sparingly if it materially improves readability, but do not over-format the answer.

                Return exactly these lines:
                detectedProblem: <one precise short technical label scoped as narrowly as the evidence allows; plain text, no bullet list>
                summary: <a short markdown block in Polish: one concise opening sentence and then 2-5 markdown bullet lines covering strongest evidence, where in the broader functional flow the failure happens, likely failure domain, and visibility limits>
                recommendedAction: <2-4 concise markdown bullet lines in Polish, ordered by priority, each saying who should act next and what should be verified or changed>
                rationale: <3-6 concise markdown bullet lines in Polish covering confirmed evidence, why this diagnosis fits best, what the surrounding flow in our system appears to be, and what still requires confirmation or external access>
                affectedFunction: <a short markdown block in Polish based on the broader GitLab exploration: one concise opening sentence and then 2-5 markdown bullet lines describing the affected system function, its role in the broader flow, key upstream/downstream collaborators, and where the incident interrupts that flow>
                affectedProcess: <short Polish plain-text label for the most likely affected process, grounded in operational-context evidence when available; write `nieustalone` if the process is not grounded>
                affectedBoundedContext: <short Polish plain-text label for the most likely affected bounded context, grounded in operational-context evidence when available; write `nieustalone` if the context is not grounded>
                affectedTeam: <short Polish plain-text label for the team that should currently own or receive the handoff, grounded in operational-context evidence when available; write `nieustalone` if the owner is not grounded>

                Attached artifacts:
                %s

                Available tools:
                %s
                """.formatted(
                request.correlationId(),
                renderEnvironment(request.environment()),
                renderGitLabBranch(request.gitLabBranch()),
                renderGitLabGroup(request.gitLabGroup()),
                formatAttachedArtifacts(artifactDescriptors),
                formatAvailableTools(tools)
        );
    }

    private String formatAvailableTools(Iterable<ToolDefinition> tools) {
        var rendered = new StringBuilder();

        for (var tool : tools) {
            if (rendered.length() > 0) {
                rendered.append(System.lineSeparator());
            }

            rendered.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description());
        }

        if (rendered.length() == 0) {
            return "- none";
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
