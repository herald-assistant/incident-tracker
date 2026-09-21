package pl.mkn.tdw.features.uxinspector.ai.copilot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Component
public class UxInspectorCopilotToolSessionContextFactory {
    public CopilotToolSessionContext create(String runReference, UxInspectorTargetContext context) {
        var runId = StringUtils.hasText(runReference) ? runReference.trim() : UUID.randomUUID().toString();
        var hidden = new LinkedHashMap<String, Object>();
        hidden.put(UxInspectorCopilotContextKeys.FEATURE, UxInspectorCopilotContextKeys.FEATURE_VALUE);
        hidden.put(UxInspectorCopilotContextKeys.SYSTEM_ID, context.systemId());
        hidden.put(UxInspectorCopilotContextKeys.SOURCE_REVISION, context.sourceRevision().revision());
        hidden.put(UxInspectorCopilotContextKeys.VIEW_ID, context.view().viewId());
        hidden.put(AgentToolContextKeys.REPORT_ID, "ux-inspector-report-" + runId);
        hidden.put(AgentToolContextKeys.REPORT_FEATURE, UxInspectorCopilotContextKeys.FEATURE_VALUE);
        hidden.put(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS, List.of("answer"));
        hidden.put(AgentToolContextKeys.TOOL_BUDGET_POLICY, AgentToolContextKeys.TOOL_BUDGET_POLICY_GOAL_DRIVEN);
        hidden.put(AgentToolContextKeys.GITLAB_GROUP, context.sourceScope().group());
        hidden.put(AgentToolContextKeys.GITLAB_BRANCH, context.sourceRevision().branch());
        hidden.put(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE, new GitLabRepositoryToolScope(
                context.sourceScope().group(),
                context.sourceScope().projectName(),
                context.sourceRevision().branch(),
                context.sourceRevision().revision()
        ));
        hidden.put(GitLabFrontendToolContextKeys.PROJECT_NAME, context.sourceScope().projectName());
        hidden.put(GitLabFrontendToolContextKeys.PATH_PREFIXES, context.sourceScope().pathPrefixes());
        hidden.put(GitLabFrontendToolContextKeys.SOURCE_REVISION, context.sourceRevision().revision());
        hidden.put(GitLabFrontendToolContextKeys.SCREEN_SLICE_REF, context.view().viewId());
        return new CopilotToolSessionContext(runId, "ux-inspector-" + runId, hidden);
    }

    public CopilotToolSessionContext createFollowUp(
            String runReference,
            String copilotSessionId,
            UxInspectorTargetContext context
    ) {
        var created = create(runReference, context);
        return new CopilotToolSessionContext(
                created.analysisRunId(),
                StringUtils.hasText(copilotSessionId) ? copilotSessionId.trim() : created.copilotSessionId(),
                created.hiddenContext()
        );
    }
}
