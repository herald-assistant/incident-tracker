package pl.mkn.tdw.features.configdriftviewer.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotNamedSkillDirectoryResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation.ConfigDriftViewerPromptPreparation;
import pl.mkn.tdw.features.configdriftviewer.ai.report.ConfigDriftViewerReportFactory;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
@RequiredArgsConstructor
public class ConfigDriftViewerCopilotRunRequestAssembler {

    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("config-drift-viewer");
    private static final String DENIED_TOOL_MESSAGE =
            "Use only sanitized Config Drift Viewer artifacts and explicitly enabled scoped tools.";

    private final CopilotSdkToolFactory toolFactory;
    private final ConfigDriftViewerCopilotToolSessionContextFactory contextFactory;
    private final CopilotNamedSkillDirectoryResolver skillDirectoryResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final ConfigDriftViewerReportFactory reportFactory;

    public ConfigDriftViewerCopilotRunAssembly assemble(
            String runReference,
            ConfigDriftViewerJobStartRequest request,
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerDeepContext deepContext,
            ConfigDriftViewerPromptPreparation preparation,
            AnalysisAiAuthRef authRef
    ) {
        if (request == null || request.mode() != ConfigDriftViewerMode.DEEP) {
            throw new IllegalArgumentException("Copilot runtime is available only for DEEP verification.");
        }
        var skills = ConfigDriftViewerCopilotRuntimeSkillNames.deepReview();
        var skillDirectories = skillDirectoryResolver.resolveSkillDirectories(skills);
        if (skillDirectories.isEmpty()) {
            throw new IllegalStateException("Config Drift Viewer Copilot skill was not resolved.");
        }
        var toolContext = contextFactory.create(runReference, request, deepContext);
        var accessPolicy = ConfigDriftViewerCopilotToolAccessPolicy.fromRegisteredTools(
                toolFactory.createToolDefinitions(toolContext, DESCRIPTION_CONTEXT)
        );
        var sessionConfig = new CopilotSessionConfigRequest(
                toolContext.copilotSessionId(),
                accessPolicy.enabledTools(),
                accessPolicy.availableToolNames(),
                skillDirectories,
                new CopilotModelSelection(request.model(), request.reasoningEffort()),
                DENIED_TOOL_MESSAGE
        );
        var reportId = (String) toolContext.hiddenContext().get(AgentToolContextKeys.REPORT_ID);
        var initialReport = reportFactory.createInitialReport(reportId, deterministic, deepContext);
        var runRequest = new CopilotRunRequest(
                toolContext.analysisRunId(),
                runAuthMapper.toRunAuth(authRef),
                CopilotSessionTarget.newSession(),
                preparation.prompt(),
                sessionConfig,
                preparation.artifactContents(),
                null
        ).withInitialReport(initialReport);
        return new ConfigDriftViewerCopilotRunAssembly(runRequest, toolContext, accessPolicy);
    }
}
