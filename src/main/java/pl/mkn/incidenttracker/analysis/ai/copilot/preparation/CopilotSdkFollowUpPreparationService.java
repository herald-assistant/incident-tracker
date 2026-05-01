package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotPreparedSessionFactory;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;

@Service
@RequiredArgsConstructor
public class CopilotSdkFollowUpPreparationService {

    private final CopilotFollowUpRunAssembler runAssembler;
    private final CopilotPreparedSessionFactory preparedSessionFactory;

    public CopilotPreparedSession prepare(AnalysisAiChatRequest request) {
        return preparedSessionFactory.prepare(runAssembler.assemble(request));
    }
}
