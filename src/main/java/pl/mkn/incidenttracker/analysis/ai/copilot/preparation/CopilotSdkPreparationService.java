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
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
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

    public CopilotSdkPreparedRequest prepare(AnalysisAiAnalysisRequest request) {
        var tools = toolBridge.buildToolDefinitions();
        var skillDirectories = skillRuntimeLoader.resolveSkillDirectories();
        var clientOptions = new CopilotClientOptions()
                .setUseLoggedInUser(true)
                .setCwd(properties.getWorkingDirectory());

        if (properties.getCliPath() != null && !properties.getCliPath().isBlank()) {
            clientOptions.setCliPath(properties.getCliPath());
        }

        if (properties.getGithubToken() != null && !properties.getGithubToken().isBlank()) {
            clientOptions
                    .setGithubToken(properties.getGithubToken())
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

        String prompt = buildPrompt(request, tools);
        var messageOptions = new MessageOptions()
                .setPrompt(prompt);

        return new CopilotSdkPreparedRequest(
                request.correlationId(),
                clientOptions,
                sessionConfig,
                messageOptions,
                prompt
        );
    }

    private String buildPrompt(AnalysisAiAnalysisRequest request, List<ToolDefinition> tools) {
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

                Analyze only the evidence below.
                Do not assume facts that are not supported by the evidence.
                Follow any loaded skills that are relevant for incident analysis and tool usage.
                When using GitLab tools, keep the provided gitLabGroup and gitLabBranch unchanged.
                Treat Dynatrace evidence as initial runtime context only. No Dynatrace tools are available during the session.
                If gitLabBranch is not resolved from logs, do not invent one.
                Infer project names and file paths only from evidence and repository exploration.
                If the evidence already contains a concrete exception stacktrace and matching GitLab code around the failing line, answer directly instead of calling more tools.
                When GitLab exploration is needed, do not stop at the single failing line if understanding the surrounding functional or technical flow would materially improve the diagnosis, handoff, or next step.
                If one focused code read is not enough for a newcomer to understand the incident, expand the exploration to the surrounding method, class, service flow, and a few directly collaborating files or integration points.
                Prefer to explain where in the broader request or business flow the failure happens, what leads into that point, what happens immediately after it, and which downstream systems or components are involved.
                The available evidence comes only from our system: Elasticsearch logs, Dynatrace runtime signals, and GitLab code context.
                The real cause may still be in an external integration, downstream service, platform configuration, database state, messaging layer,
                infrastructure, or an area owned by another team that is not directly visible here.
                If the likely problem is outside our codebase or outside the telemetry currently available, say that explicitly.
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

                Available tools:
                %s

                Evidence sections:
                %s
                """.formatted(
                request.correlationId(),
                renderEnvironment(request.environment()),
                renderGitLabBranch(request.gitLabBranch()),
                renderGitLabGroup(request.gitLabGroup()),
                formatAvailableTools(tools),
                formatEvidenceSections(request.evidenceSections())
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

    private String formatEvidenceSections(Iterable<AnalysisEvidenceSection> sections) {
        var rendered = new StringBuilder();

        for (var section : sections) {
            if (rendered.length() > 0) {
                rendered.append(System.lineSeparator()).append(System.lineSeparator());
            }

            rendered.append("Provider: ")
                    .append(section.provider())
                    .append(", category: ")
                    .append(section.category())
                    .append(System.lineSeparator())
                    .append(formatEvidenceItems(section.items()));
        }

        if (rendered.length() == 0) {
            return "Provider: none, category: none" + System.lineSeparator() + "- none";
        }

        return rendered.toString();
    }

    private String formatEvidenceItems(Iterable<AnalysisEvidenceItem> items) {
        var rendered = new StringBuilder();

        for (var item : items) {
            if (rendered.length() > 0) {
                rendered.append(System.lineSeparator());
            }
            rendered.append("- ")
                    .append(item.title())
                    .append(" | ")
                    .append(formatAttributes(item.attributes()));
        }

        if (rendered.length() == 0) {
            return "- none";
        }

        return rendered.toString();
    }

    private String formatAttributes(Iterable<AnalysisEvidenceAttribute> attributes) {
        var rendered = new StringBuilder();

        for (var attribute : attributes) {
            if (rendered.length() > 0) {
                rendered.append(" | ");
            }
            rendered.append(attribute.name())
                    .append("=")
                    .append(attribute.value());
        }

        if (rendered.length() == 0) {
            return "no-attributes";
        }

        return rendered.toString();
    }

}
