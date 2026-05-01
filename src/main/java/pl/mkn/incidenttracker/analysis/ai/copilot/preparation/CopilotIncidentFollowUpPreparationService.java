package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;

@Service
@RequiredArgsConstructor
public class CopilotIncidentFollowUpPreparationService {

    private final CopilotIncidentFollowUpRunAssembler runAssembler;
    private final CopilotRunPreparationService runPreparationService;

    public CopilotPreparedSession prepare(AnalysisAiChatRequest request) {
        return runPreparationService.prepare(runAssembler.assemble(request));
    }
}
