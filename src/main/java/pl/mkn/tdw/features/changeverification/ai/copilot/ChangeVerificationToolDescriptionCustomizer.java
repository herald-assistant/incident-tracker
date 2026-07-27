package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer;

import java.util.List;

@Component
public class ChangeVerificationToolDescriptionCustomizer implements CopilotToolDescriptionCustomizer {

    private static final String PROFILE_ID = "change-verification";
    private static final String GUIDANCE_HEADER = "Change Verification guidance:";

    @Override
    public String customize(CopilotToolDescriptionContext descriptionContext, String toolName, String description) {
        var baseDescription = StringUtils.hasText(description) ? description.trim() : "";
        if (!supports(descriptionContext)) {
            return baseDescription;
        }

        var guidance = guidanceFor(toolName);
        if (guidance.isEmpty() || baseDescription.contains(GUIDANCE_HEADER)) {
            return baseDescription;
        }

        var builder = new StringBuilder(baseDescription);
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(GUIDANCE_HEADER);
        for (var line : guidance) {
            builder.append("\n- ").append(line);
        }
        return builder.toString();
    }

    private boolean supports(CopilotToolDescriptionContext descriptionContext) {
        return descriptionContext != null && descriptionContext.matchesProfile(PROFILE_ID);
    }

    private List<String> guidanceFor(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return List.of();
        }

        var normalizedToolName = toolName.trim();
        if (GitLabToolNames.LIST_AVAILABLE_REPOSITORIES.equals(normalizedToolName)) {
            return List.of(
                    "Use when Repository Scope does not provide a grounded projectName or when operational context is needed to choose related repositories.",
                    "Use returned projectName values as inputs for later GitLab search, flow and read tools; use gitLabPath only as display/context.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (normalizedToolName.startsWith(GitLabToolNames.PREFIX)) {
            return List.of(
                    "Use projectName exactly from change-verification/repository-scope.md or a previous GitLab tool result.",
                    "Do not pass projectPath, gitLabPath, rootGroup/projectName or the full merge-request path as projectName.",
                    "Pass branchRef from analysisRef in change-verification/repository-scope.md; sourceRef and targetRef are merge-request context only.",
                    "Use Operational Context tools when you need to understand the system, bounded context, process or code-search scope behind the repository.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (normalizedToolName.startsWith(OperationalContextToolNames.PREFIX)) {
            return List.of(
                    "Use operational context for catalog grounding, code-search scope, system, bounded context, process, integration or vocabulary.",
                    "Do not treat repository identity alone as proof of ownership or business scope.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        return List.of();
    }
}
