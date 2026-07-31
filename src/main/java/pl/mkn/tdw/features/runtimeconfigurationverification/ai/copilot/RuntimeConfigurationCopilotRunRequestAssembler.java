package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

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
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparation;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.report.RuntimeConfigurationReportFactory;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
@RequiredArgsConstructor
public class RuntimeConfigurationCopilotRunRequestAssembler {

    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("runtime-configuration-verification");
    private static final String DENIED_TOOL_MESSAGE =
            "Use only sanitized Runtime Configuration Verification artifacts and explicitly enabled scoped tools.";

    private final CopilotSdkToolFactory toolFactory;
    private final RuntimeConfigurationCopilotToolSessionContextFactory contextFactory;
    private final CopilotNamedSkillDirectoryResolver skillDirectoryResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final RuntimeConfigurationReportFactory reportFactory;

    public RuntimeConfigurationCopilotRunAssembly assemble(
            String runReference,
            RuntimeConfigurationVerificationJobStartRequest request,
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deepContext,
            RuntimeConfigurationPromptPreparation preparation,
            AnalysisAiAuthRef authRef
    ) {
        if (request == null || request.mode() != RuntimeConfigurationVerificationMode.DEEP) {
            throw new IllegalArgumentException("Copilot runtime is available only for DEEP verification.");
        }
        var skills = RuntimeConfigurationCopilotRuntimeSkillNames.deepReview();
        var skillDirectories = skillDirectoryResolver.resolveSkillDirectories(skills);
        if (skillDirectories.isEmpty()) {
            throw new IllegalStateException("Runtime Configuration Verification Copilot skill was not resolved.");
        }
        var toolContext = contextFactory.create(runReference, request, deepContext);
        var accessPolicy = RuntimeConfigurationCopilotToolAccessPolicy.fromRegisteredTools(
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
        return new RuntimeConfigurationCopilotRunAssembly(runRequest, toolContext, accessPolicy);
    }
}
