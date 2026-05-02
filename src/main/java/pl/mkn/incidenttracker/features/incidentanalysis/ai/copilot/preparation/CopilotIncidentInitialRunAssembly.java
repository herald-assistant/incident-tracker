package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunRequest;
import java.util.Objects;

public record CopilotIncidentInitialRunAssembly(
        CopilotRunRequest runRequest
) {

    public CopilotIncidentInitialRunAssembly {
        Objects.requireNonNull(runRequest, "runRequest must not be null");
    }
}
