package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.uiexplorer.ai.readiness.UiExplorerAiReadiness;

public record UiExplorerCopilotRunAssembly(
        CopilotRunRequest runRequest,
        CopilotToolSessionContext toolSessionContext,
        UiExplorerCopilotToolAccessPolicy toolAccessPolicy,
        UiExplorerAiReadiness readiness
) {
}
