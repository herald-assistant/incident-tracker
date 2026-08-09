package pl.mkn.tdw.features.configdriftviewer.ai.copilot;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public record ConfigDriftViewerCopilotRunAssembly(
        CopilotRunRequest runRequest,
        CopilotToolSessionContext toolSessionContext,
        ConfigDriftViewerCopilotToolAccessPolicy toolAccessPolicy
) {
}
