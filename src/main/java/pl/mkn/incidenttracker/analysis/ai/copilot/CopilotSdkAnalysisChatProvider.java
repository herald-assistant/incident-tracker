package pl.mkn.incidenttracker.analysis.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiChatProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiChatResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkFollowUpPreparationService;

@Service
@RequiredArgsConstructor
public class CopilotSdkAnalysisChatProvider implements AnalysisAiChatProvider {

    private final CopilotSdkFollowUpPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;

    @Override
    public AnalysisAiChatResponse chat(
            AnalysisAiChatRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        try (var preparedRequest = preparationService.prepare(request)) {
            var content = executionGateway.execute(
                    preparedRequest,
                    toolEvidenceListener != null ? toolEvidenceListener : AnalysisAiToolEvidenceListener.NO_OP
            );

            return new AnalysisAiChatResponse(
                    "copilot-sdk",
                    content != null ? content.trim() : "",
                    preparedRequest.prompt()
            );
        }
    }
}
