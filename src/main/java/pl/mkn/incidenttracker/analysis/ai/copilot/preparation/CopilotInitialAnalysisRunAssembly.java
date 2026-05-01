package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRunRequest;
import java.util.Objects;

public record CopilotInitialAnalysisRunAssembly(
        CopilotRunRequest runRequest,
        CopilotInitialAnalysisPreparationMetrics metrics
) {

    public CopilotInitialAnalysisRunAssembly {
        Objects.requireNonNull(runRequest, "runRequest must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }
}
