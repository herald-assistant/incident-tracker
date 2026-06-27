package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;

@Service
@RequiredArgsConstructor
public class CopilotIncidentFollowUpPreparationService {

    private final CopilotIncidentFollowUpRunAssembler runAssembler;
    private final CopilotRunPreparationService runPreparationService;

    public CopilotPreparedSession prepare(AnalysisAiChatRequest request) {
        return runPreparationService.prepare(runAssembler.assemble(request));
    }
}
