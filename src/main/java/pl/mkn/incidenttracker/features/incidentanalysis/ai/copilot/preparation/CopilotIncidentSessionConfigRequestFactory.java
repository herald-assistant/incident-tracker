package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiOptions;

@Component
@RequiredArgsConstructor
public class CopilotIncidentSessionConfigRequestFactory {

    private static final String INCIDENT_TOOL_DENIED_MESSAGE =
            "Use only the inline incident artifacts and the explicitly enabled incident-analysis tools for this session.";

    private final CopilotSkillRuntimeLoader skillRuntimeLoader;

    public CopilotSessionConfigRequest create(
            String copilotSessionId,
            CopilotIncidentToolAccessPolicy toolAccessPolicy,
            AnalysisAiOptions options
    ) {
        return new CopilotSessionConfigRequest(
                copilotSessionId,
                toolAccessPolicy.enabledTools(),
                toolAccessPolicy.availableToolNames(),
                skillRuntimeLoader.resolveSkillDirectories(),
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
