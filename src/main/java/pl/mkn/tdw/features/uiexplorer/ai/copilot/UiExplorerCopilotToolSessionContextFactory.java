package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendTypeScriptSliceTarget;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.features.uiexplorer.report.UiExplorerReportSectionIds;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Component
public class UiExplorerCopilotToolSessionContextFactory {

    private static final String SESSION_PREFIX = "ui-explorer-";

    public CopilotToolSessionContext create(
            String runReference,
            UiExplorerJobStartRequest request,
            UiExplorerScreenReachabilityContext context
    ) {
        if (context == null || context.sourceScope() == null || context.sourceRevision() == null) {
            throw new IllegalArgumentException("Resolved UI Explorer source scope is required.");
        }
        var runId = StringUtils.hasText(runReference) ? runReference.trim() : UUID.randomUUID().toString();
        var scope = context.sourceScope();
        var hidden = new LinkedHashMap<String, Object>();
        hidden.put(UiExplorerCopilotToolContextKeys.FEATURE, UiExplorerCopilotToolContextKeys.FEATURE_VALUE);
        hidden.put(UiExplorerCopilotToolContextKeys.RUN_KIND, UiExplorerCopilotToolContextKeys.RUN_KIND_INITIAL);
        hidden.put(AgentToolContextKeys.REPORT_ID, "ui-explorer-report-" + runId);
        hidden.put(AgentToolContextKeys.REPORT_FEATURE, UiExplorerCopilotToolContextKeys.FEATURE_VALUE);
        hidden.put(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS, UiExplorerReportSectionIds.activeSectionIds(request));
        hidden.put(
                AgentToolContextKeys.TOOL_BUDGET_POLICY,
                AgentToolContextKeys.TOOL_BUDGET_POLICY_GOAL_DRIVEN
        );
        hidden.put(UiExplorerCopilotToolContextKeys.SYSTEM_ID, context.systemId());
        hidden.put(UiExplorerCopilotToolContextKeys.SOURCE_REVISION, context.sourceRevision().revision());
        hidden.put(AgentToolContextKeys.GITLAB_GROUP, scope.gitLabGroup());
        hidden.put(AgentToolContextKeys.GITLAB_BRANCH, scope.ref());
        hidden.put(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES, List.of(context.systemId()));
        hidden.put(GitLabFrontendToolContextKeys.PROJECT_NAME, scope.projectName());
        hidden.put(GitLabFrontendToolContextKeys.PATH_PREFIXES, scope.pathPrefixes());
        hidden.put(GitLabFrontendToolContextKeys.SOURCE_REVISION, context.sourceRevision().revision());
        hidden.put(GitLabFrontendToolContextKeys.SCREEN_SLICE_REF, context.screen().screenId());
        hidden.put(GitLabFrontendToolContextKeys.TYPESCRIPT_SLICE_TARGETS, typeScriptSliceTargets(context));
        return new CopilotToolSessionContext(runId, SESSION_PREFIX + runId, hidden);
    }

    private java.util.Map<String, GitLabFrontendTypeScriptSliceTarget> typeScriptSliceTargets(
            UiExplorerScreenReachabilityContext context
    ) {
        var targets = new LinkedHashMap<String, GitLabFrontendTypeScriptSliceTarget>();
        context.components().stream()
                .filter(component -> StringUtils.hasText(component.componentId())
                        && StringUtils.hasText(component.sourcePath()))
                .forEach(component -> targets.put(component.componentId(), new GitLabFrontendTypeScriptSliceTarget(
                        component.componentId(),
                        component.sourcePath(),
                        component.symbol(),
                        component.templatePath(),
                        component.entrySymbols().stream()
                                .map(candidate -> new GitLabTypeScriptSymbolSelector(
                                        candidate.symbolName(), candidate.kind(), candidate.lineStart()
                                ))
                                .toList()
                )));
        if (context.graph() != null) {
            context.graph().dependencies().stream()
                    .filter(dependency -> StringUtils.hasText(dependency.dependencyId())
                            && StringUtils.hasText(dependency.sourcePath()))
                    .forEach(dependency -> targets.put(
                            dependency.dependencyId(),
                            new GitLabFrontendTypeScriptSliceTarget(
                                    dependency.dependencyId(),
                                    dependency.sourcePath(),
                                    dependency.symbol(),
                                    null,
                                    dependency.methods().stream()
                                            .filter(StringUtils::hasText)
                                            .map(method -> new GitLabTypeScriptSymbolSelector(
                                                    method, GitLabTypeScriptSymbolKind.AUTO, null
                                            ))
                                            .toList()
                            )
                    ));
        }
        return java.util.Map.copyOf(targets);
    }
}
