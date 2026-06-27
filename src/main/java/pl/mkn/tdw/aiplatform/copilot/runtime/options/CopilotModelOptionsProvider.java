package pl.mkn.tdw.aiplatform.copilot.runtime.options;

import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

public interface CopilotModelOptionsProvider {

    CopilotModelOptionsResponse modelOptions(CopilotRunAuth auth);
}
