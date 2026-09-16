package pl.mkn.tdw.features.uxinspector.ai.copilot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendTypeScriptSliceTarget;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        hidden.put(GitLabFrontendToolContextKeys.TYPESCRIPT_SLICE_TARGETS, typeScriptTargets(context));
        return new CopilotToolSessionContext(runId, "ux-inspector-" + runId, hidden);
    }

    private Map<String, GitLabFrontendTypeScriptSliceTarget> typeScriptTargets(UxInspectorTargetContext context) {
        var targets = new LinkedHashMap<String, GitLabFrontendTypeScriptSliceTarget>();
        context.graph().componentLevels().stream().flatMap(level -> level.components().stream())
                .filter(value -> StringUtils.hasText(value.componentId()) && StringUtils.hasText(value.sourcePath()))
                .forEach(value -> targets.put(value.componentId(), new GitLabFrontendTypeScriptSliceTarget(
                        value.componentId(), value.sourcePath(), value.symbol(), value.templatePath(),
                        value.entrySymbols().stream().map(symbol -> new GitLabTypeScriptSymbolSelector(
                                symbol.symbolName(), symbol.kind(), symbol.lineStart())).toList())));
        context.graph().dependencies().stream()
                .filter(value -> StringUtils.hasText(value.dependencyId()) && StringUtils.hasText(value.sourcePath()))
                .forEach(value -> targets.put(value.dependencyId(), new GitLabFrontendTypeScriptSliceTarget(
                        value.dependencyId(), value.sourcePath(), value.symbol(), null,
                        value.methods().stream().filter(StringUtils::hasText)
                                .map(method -> new GitLabTypeScriptSymbolSelector(method, GitLabTypeScriptSymbolKind.AUTO, null))
                                .toList())));
        return Map.copyOf(targets);
    }
}
