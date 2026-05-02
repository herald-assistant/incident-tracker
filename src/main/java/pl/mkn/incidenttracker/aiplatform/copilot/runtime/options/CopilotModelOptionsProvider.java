package pl.mkn.incidenttracker.aiplatform.copilot.runtime.options;

import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotRunAuth;

public interface CopilotModelOptionsProvider {

    CopilotModelOptionsResponse modelOptions(CopilotRunAuth auth);
}
