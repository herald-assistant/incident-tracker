package pl.mkn.tdw.features.operationalcontextassistance.ai;

import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;

public record OperationalContextAssistanceCopilotRunAssembly(
        CopilotRunRequest runRequest,
        GitLabRepositoryToolScope sourceScope
) {
}
