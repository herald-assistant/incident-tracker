package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

@Component
public class CopilotIncidentSessionConfigRequestFactory {

    private static final String INCIDENT_TOOL_DENIED_MESSAGE =
            "Use only the inline incident artifacts and the explicitly enabled incident-analysis tools for this session.";

    public CopilotSessionConfigRequest create(
            String copilotSessionId,
            CopilotIncidentToolAccessPolicy toolAccessPolicy,
            AnalysisAiOptions options
    ) {
        return new CopilotSessionConfigRequest(
                copilotSessionId,
                toolAccessPolicy.enabledTools(),
                toolAccessPolicy.availableToolNames(),
                modelSelection(options),
                INCIDENT_TOOL_DENIED_MESSAGE
        );
    }

    private CopilotModelSelection modelSelection(AnalysisAiOptions options) {
        return options != null
                ? new CopilotModelSelection(options.model(), options.reasoningEffort())
                : CopilotModelSelection.DEFAULT;
    }
}
