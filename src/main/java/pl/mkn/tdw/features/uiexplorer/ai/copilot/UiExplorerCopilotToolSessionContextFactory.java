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
                UiExplorerCopilotToolContextKeys.EMBEDDED_SOURCE_PATHS,
                context.sourceFiles().stream().map(file -> file.path()).toList()
        );
        hidden.put(
                UiExplorerCopilotToolContextKeys.TRUNCATED_SOURCE_PATHS,
                context.sourceFiles().stream().filter(file -> file.truncated()).map(file -> file.path()).toList()
        );
        return new CopilotToolSessionContext(runId, SESSION_PREFIX + runId, hidden);
    }
}
