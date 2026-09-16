package pl.mkn.tdw.features.uxinspector.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorPromptPreparation;
import pl.mkn.tdw.features.uxinspector.ai.tools.UxInspectorTargetToolSetFactory;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportFactory;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
@RequiredArgsConstructor
public class UxInspectorCopilotRunRequestAssembler {
    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT = CopilotToolDescriptionContext.profile("ux-inspector");
    private final CopilotSdkToolFactory toolFactory;
    private final UxInspectorCopilotToolSessionContextFactory contextFactory;
    private final UxInspectorTargetToolSetFactory targetToolSetFactory;
    private final CopilotRunAuthMapper authMapper;
    private final UxInspectorReportFactory reportFactory;

    public UxInspectorCopilotRunAssembly assemble(String runReference, UxInspectorJobStartRequest request,
                                                  UxInspectorTargetContext context,
                                                  UxInspectorPromptPreparation preparation,
                                                  AnalysisAiAuthRef authRef) {
        var toolContext = contextFactory.create(runReference, context);
        var targetTools = targetToolSetFactory.create(toolContext.analysisRunId(), context);
        var registered = toolFactory.createToolDefinitions(toolContext, DESCRIPTION_CONTEXT, targetTools.callbacks());
        var access = UxInspectorCopilotToolAccessPolicy.from(registered);
        var sessionConfig = new CopilotSessionConfigRequest(toolContext.copilotSessionId(), access.enabledTools(),
                access.availableToolNames(), new CopilotModelSelection(request.model(), request.reasoningEffort()),
                "Use only UX Inspector target/source/report tools in the pinned scope.", false)
                .withDurableSystemInstructions(UxInspectorDurableSystemInstructions.render(preparation));
        var initialReport = reportFactory.create((String) toolContext.hiddenContext().get(AgentToolContextKeys.REPORT_ID), context);
        var run = new CopilotRunRequest(toolContext.analysisRunId(), authMapper.toRunAuth(authRef),
                CopilotSessionTarget.newSession(), preparation.prompt(), sessionConfig, preparation.artifactContents(), null)
                .withInitialReport(initialReport);
        var repositoryScope = (GitLabRepositoryToolScope) toolContext.hiddenContext()
                .get(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE);
        return new UxInspectorCopilotRunAssembly(run, toolContext, repositoryScope, access, targetTools);
    }
}
