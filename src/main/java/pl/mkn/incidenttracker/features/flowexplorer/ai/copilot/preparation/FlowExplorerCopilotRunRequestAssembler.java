package pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.incidenttracker.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

@Component
@RequiredArgsConstructor
public class FlowExplorerCopilotRunRequestAssembler {

    private static final CopilotToolDescriptionContext TOOL_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("flow-explorer");

    private final CopilotSdkToolFactory toolFactory;
    private final FlowExplorerCopilotToolSessionContextFactory toolSessionContextFactory;
    private final FlowExplorerCopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final FlowExplorerCopilotSessionConfigRequestFactory sessionConfigRequestFactory;

    public FlowExplorerCopilotRunAssembly assemble(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation
    ) {
        return assemble(runReference, request, contextSnapshot, preparation, false);
    }

    public FlowExplorerCopilotRunAssembly assembleFollowUp(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation
    ) {
        return assemble(runReference, request, contextSnapshot, preparation, true);
    }

    private FlowExplorerCopilotRunAssembly assemble(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            boolean followUp
    ) {
        var toolSessionContext = toolSessionContextFactory.create(
                runReference,
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
                        aiOptions
                );
        var runRequest = new CopilotRunRequest(
                toolSessionContext.analysisRunId(),
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
