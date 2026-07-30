package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public record RuntimeConfigurationCopilotRunAssembly(
        CopilotRunRequest runRequest,
        CopilotToolSessionContext toolSessionContext,
        RuntimeConfigurationCopilotToolAccessPolicy toolAccessPolicy
) {
}
