package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.List;

@Component
public class FlowExplorerCopilotSessionConfigRequestFactory {

    private static final String FLOW_EXPLORER_TOOL_DENIED_MESSAGE =
            "Use only the inline Flow Explorer artifacts and the explicitly enabled Flow Explorer tools for this session.";

    public CopilotSessionConfigRequest create(
            String copilotSessionId,
            FlowExplorerCopilotToolAccessPolicy toolAccessPolicy,
            AnalysisAiOptions options
    ) {
        return createSessionConfig(copilotSessionId, toolAccessPolicy, options);
    }

    public CopilotSessionConfigRequest createForFollowUp(
            String copilotSessionId,
            FlowExplorerCopilotToolAccessPolicy toolAccessPolicy,
            AnalysisAiOptions options
    ) {
        return createSessionConfig(copilotSessionId, toolAccessPolicy, options);
    }

    private CopilotSessionConfigRequest createSessionConfig(
            String copilotSessionId,
            FlowExplorerCopilotToolAccessPolicy toolAccessPolicy,
            AnalysisAiOptions options
    ) {
        var policy = toolAccessPolicy != null
                ? toolAccessPolicy
                : FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of());

        return new CopilotSessionConfigRequest(
                copilotSessionId,
                policy.enabledTools(),
                policy.availableToolNames(),
                modelSelection(options),
                FLOW_EXPLORER_TOOL_DENIED_MESSAGE
        );
    }

    private CopilotModelSelection modelSelection(AnalysisAiOptions options) {
        return options != null
                ? new CopilotModelSelection(options.model(), options.reasoningEffort())
                : CopilotModelSelection.DEFAULT;
    }
}
