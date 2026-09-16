package pl.mkn.tdw.features.uxinspector.ai.copilot;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.features.uxinspector.ai.tools.UxInspectorTargetToolSet;

public record UxInspectorCopilotRunAssembly(CopilotRunRequest runRequest,
                                             CopilotToolSessionContext toolSessionContext,
                                             GitLabRepositoryToolScope repositoryToolScope,
                                             UxInspectorCopilotToolAccessPolicy toolAccessPolicy,
                                             UxInspectorTargetToolSet targetToolSet) {}
