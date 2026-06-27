package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
public class FlowExplorerCopilotRunRequestAssembler {

    private static final CopilotToolDescriptionContext TOOL_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("flow-explorer");

    private final CopilotSdkToolFactory toolFactory;
    private final FlowExplorerCopilotToolSessionContextFactory toolSessionContextFactory;
    private final FlowExplorerCopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final FlowExplorerCopilotSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotRunAuthMapper runAuthMapper;

    @Autowired
    public FlowExplorerCopilotRunRequestAssembler(
            CopilotSdkToolFactory toolFactory,
            FlowExplorerCopilotToolSessionContextFactory toolSessionContextFactory,
            FlowExplorerCopilotToolAccessPolicyFactory toolAccessPolicyFactory,
            FlowExplorerCopilotSessionConfigRequestFactory sessionConfigRequestFactory,
            CopilotRunAuthMapper runAuthMapper
    ) {
        this.toolFactory = toolFactory;
        this.toolSessionContextFactory = toolSessionContextFactory;
        this.toolAccessPolicyFactory = toolAccessPolicyFactory;
        this.sessionConfigRequestFactory = sessionConfigRequestFactory;
        this.runAuthMapper = runAuthMapper;
    }

    public FlowExplorerCopilotRunRequestAssembler(
            CopilotSdkToolFactory toolFactory,
            FlowExplorerCopilotToolSessionContextFactory toolSessionContextFactory,
            FlowExplorerCopilotToolAccessPolicyFactory toolAccessPolicyFactory,
            FlowExplorerCopilotSessionConfigRequestFactory sessionConfigRequestFactory
    ) {
        this(
                toolFactory,
                toolSessionContextFactory,
                toolAccessPolicyFactory,
                sessionConfigRequestFactory,
                new CopilotRunAuthMapper()
        );
    }

    public FlowExplorerCopilotRunAssembly assemble(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation
    ) {
        return assemble(runReference, request, contextSnapshot, preparation, AnalysisAiAuthRef.localToken(null));
    }

    public FlowExplorerCopilotRunAssembly assemble(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            AnalysisAiAuthRef authRef
    ) {
        return assemble(runReference, request, contextSnapshot, preparation, false, null, authRef);
    }

    public FlowExplorerCopilotRunAssembly assembleFollowUp(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation
    ) {
        return assembleFollowUp(
                runReference,
                request,
                contextSnapshot,
                preparation,
                null,
                AnalysisAiAuthRef.localToken(null)
        );
    }

    public FlowExplorerCopilotRunAssembly assembleFollowUp(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            String copilotSessionId,
            AnalysisAiAuthRef authRef
    ) {
        if (!StringUtils.hasText(copilotSessionId)) {
            throw new IllegalArgumentException("Flow Explorer follow-up requires copilotSessionId for session resume.");
        }
        return assemble(runReference, request, contextSnapshot, preparation, true, copilotSessionId, authRef);
    }

    private FlowExplorerCopilotRunAssembly assemble(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            boolean followUp,
            String copilotSessionId,
            AnalysisAiAuthRef authRef
    ) {
        var toolSessionContext = toolSessionContextFactory.create(
                runReference,
                copilotSessionId,
                request,
                contextSnapshot,
                preparation,
                followUp
        );
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext, TOOL_DESCRIPTION_CONTEXT);
        var toolAccessPolicy = toolAccessPolicyFactory.create(registeredTools);
        var aiOptions = request != null ? request.aiOptions() : null;
        var sessionConfigRequest = followUp
                ? sessionConfigRequestFactory.createForFollowUp(
                        toolSessionContext.copilotSessionId(),
                        toolAccessPolicy,
                        aiOptions
                )
                : sessionConfigRequestFactory.create(
                        toolSessionContext.copilotSessionId(),
                        toolAccessPolicy,
                        aiOptions,
                        request != null ? request.goal() : null
                );
        var runRequest = new CopilotRunRequest(
                toolSessionContext.analysisRunId(),
                runAuthMapper.toRunAuth(authRef),
                followUp
                        ? CopilotSessionTarget.existing(toolSessionContext.copilotSessionId())
                        : CopilotSessionTarget.newSession(),
                preparation != null ? preparation.prompt() : "",
                sessionConfigRequest,
                preparation != null ? preparation.artifactContents() : null,
                null
        );

        return new FlowExplorerCopilotRunAssembly(
                runRequest,
                toolSessionContext,
                toolAccessPolicy
        );
    }
}
