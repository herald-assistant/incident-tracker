package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;

import java.util.Objects;

public record FlowExplorerCopilotRunAssembly(
        CopilotRunRequest runRequest,
        CopilotToolSessionContext toolSessionContext,
        FlowExplorerCopilotToolAccessPolicy toolAccessPolicy
) {

    public FlowExplorerCopilotRunAssembly {
        Objects.requireNonNull(runRequest, "runRequest must not be null");
        Objects.requireNonNull(toolSessionContext, "toolSessionContext must not be null");
        Objects.requireNonNull(toolAccessPolicy, "toolAccessPolicy must not be null");
    }
}
