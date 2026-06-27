package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatResponse;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityListener;
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
        return chat(request, toolEvidenceListener, AnalysisAiActivityListener.NO_OP);
    }

    @Override
    public AnalysisAiChatResponse chat(
            AnalysisAiChatRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        try (var preparedSession = preparationService.prepare(request)) {
            var executionResult = executionGateway.execute(
                    withRuntimeSinks(preparedSession, toolEvidenceListener, activityListener)
            );

            return new AnalysisAiChatResponse(
                    "copilot-sdk",
                    executionResult.content() != null ? executionResult.content().trim() : "",
                    preparedSession.prompt(),
                    executionResult.sessionId()
            );
        }
    }

    private CopilotPreparedSession withRuntimeSinks(
            CopilotPreparedSession preparedSession,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        var session = preparedSession;
        if (toolEvidenceListener != null && toolEvidenceListener != AnalysisAiToolEvidenceListener.NO_OP) {
            session = session.withEvidenceSink(toolEvidenceListener::onToolEvidenceUpdated);
        }
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            session = session.withActivitySink(activityListener::onAiActivity);
        }
        return session;
    }
}
