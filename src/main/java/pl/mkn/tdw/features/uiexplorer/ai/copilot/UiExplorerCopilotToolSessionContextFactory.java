package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UiExplorerCopilotToolSessionContextFactory {

    private static final String SESSION_PREFIX = "ui-explorer-";

    public CopilotToolSessionContext create(String runReference, UiExplorerSourceContextSnapshot context) {
        if (context == null || context.sourceScope() == null || context.sourceRevision() == null) {
            throw new IllegalArgumentException("Resolved UI Explorer source scope is required.");
        }
        var runId = StringUtils.hasText(runReference) ? runReference.trim() : UUID.randomUUID().toString();
        var scope = context.sourceScope();
        var hidden = new LinkedHashMap<String, Object>();
        hidden.put(UiExplorerCopilotToolContextKeys.FEATURE, UiExplorerCopilotToolContextKeys.FEATURE_VALUE);
        hidden.put(UiExplorerCopilotToolContextKeys.RUN_KIND, UiExplorerCopilotToolContextKeys.RUN_KIND_INITIAL);
        hidden.put(
                AgentToolContextKeys.TOOL_BUDGET_POLICY,
                AgentToolContextKeys.TOOL_BUDGET_POLICY_GOAL_DRIVEN
        );
        hidden.put(UiExplorerCopilotToolContextKeys.SYSTEM_ID, context.systemId());
        hidden.put(UiExplorerCopilotToolContextKeys.SOURCE_REVISION, context.sourceRevision().revision());
        hidden.put(AgentToolContextKeys.GITLAB_GROUP, scope.gitLabGroup());
        hidden.put(AgentToolContextKeys.GITLAB_BRANCH, scope.ref());
        hidden.put(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES, List.of(context.systemId()));
        hidden.put(UiExplorerCopilotToolContextKeys.ALLOWED_REPOSITORY, Map.of(
                "projectName", scope.projectName(),
                "branchRef", scope.ref(),
                "pathPrefixes", scope.pathPrefixes()
        ));
        hidden.put(
                UiExplorerCopilotToolContextKeys.DELIVERED_SLICE_IDS,
                context.sourceSlices().stream().map(slice -> slice.sliceId()).toList()
        );
        hidden.put(
                UiExplorerCopilotToolContextKeys.UNRESOLVED_FRONTIER_IDS,
                context.unresolvedFrontier().stream().map(frontier -> frontier.frontierId()).toList()
        );
        var frontendToolContext = new LinkedHashMap<String, Object>();
        frontendToolContext.put("projectName", scope.projectName());
        frontendToolContext.put("pathPrefixes", scope.pathPrefixes());
        frontendToolContext.put("screenId", context.screen().screenId());
        frontendToolContext.put("expectedRevision", context.sourceRevision().revision());
        frontendToolContext.put("deliveredSliceIds", context.sourceSlices().stream().map(slice -> slice.sliceId()).toList());
        frontendToolContext.put("frontiers", context.unresolvedFrontier().stream().map(frontier -> {
            var item = new LinkedHashMap<String, Object>();
            item.put("frontierId", frontier.frontierId());
            item.put("ownerPath", frontier.ownerPath());
            item.put("symbol", frontier.symbol());
            item.put("affectedCategories", frontier.affectedCategories());
            item.put("candidates", frontier.candidates());
            return item;
        }).toList());
        hidden.put(AgentToolContextKeys.GITLAB_FRONTEND_CONTEXT, frontendToolContext);
        return new CopilotToolSessionContext(runId, SESSION_PREFIX + runId, hidden);
    }
}
