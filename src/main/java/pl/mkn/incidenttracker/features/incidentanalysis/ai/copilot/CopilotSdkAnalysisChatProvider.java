package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatResponse;
import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentFollowUpPreparationService;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;

@Service
@RequiredArgsConstructor
public class CopilotSdkAnalysisChatProvider implements AnalysisAiChatProvider {

    private final CopilotIncidentFollowUpPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;

    @Override
    public AnalysisAiChatResponse chat(
            AnalysisAiChatRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        try (var preparedSession = preparationService.prepare(request)) {
            var content = executionGateway.execute(
                    withToolEvidenceSink(preparedSession, toolEvidenceListener)
            );

            return new AnalysisAiChatResponse(
                    "copilot-sdk",
                    content != null ? content.trim() : "",
                    preparedSession.prompt()
            );
        }
    }

    private CopilotPreparedSession withToolEvidenceSink(
            CopilotPreparedSession preparedSession,
            AnalysisAiToolEvidenceListener listener
    ) {
        if (listener == null || listener == AnalysisAiToolEvidenceListener.NO_OP) {
            return preparedSession;
        }

        return preparedSession.withEvidenceSink(listener::onToolEvidenceUpdated);
    }
}
