package pl.mkn.incidenttracker.analysis.flow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.AnalysisDataNotFoundException;
import pl.mkn.incidenttracker.analysis.AnalysisResultResponse;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollectionListener;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProviderDescriptor;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final AnalysisEvidenceCollector analysisEvidenceCollector;
    private final AnalysisAiProvider analysisAiProvider;
    private final GitLabProperties gitLabProperties;

    public AnalysisExecution analyze(String correlationId) {
        return analyze(correlationId, AnalysisExecutionListener.NO_OP);
    }

    public AnalysisExecution analyze(String correlationId, AnalysisExecutionListener listener) {
        var context = analysisEvidenceCollector.collect(correlationId, adaptEvidenceListener(listener));

        if (!context.hasAnyEvidence()) {
            throw new AnalysisDataNotFoundException(correlationId);
        }

        var deploymentContext = DeploymentContextEvidenceView.from(context);
        var aiRequest = new AnalysisAiAnalysisRequest(
                correlationId,
                deploymentContext.environment(),
                deploymentContext.gitLabBranch(),
                gitLabProperties.getGroup(),
                context.evidenceSections()
        );

        listener.onAiStarted(aiRequest, context);
        var preparedPrompt = analysisAiProvider.preparePrompt(aiRequest);
        listener.onAiPromptPrepared(aiRequest, preparedPrompt, context);

        var aiResponse = analysisAiProvider.analyze(aiRequest);
        var result = new AnalysisResultResponse(
                "COMPLETED",
                correlationId,
                aiRequest.environment(),
                aiRequest.gitLabBranch(),
                aiResponse.summary(),
                aiResponse.detectedProblem(),
                aiResponse.recommendedAction(),
                aiResponse.rationale(),
                aiResponse.prompt()
        );

        return new AnalysisExecution(
                context,
                aiRequest,
                StringUtils.hasText(preparedPrompt) ? preparedPrompt : aiResponse.prompt(),
                aiResponse,
                result
        );
    }

    public List<AnalysisEvidenceProviderDescriptor> providerDescriptors() {
        return analysisEvidenceCollector.providerDescriptors();
    }

    private AnalysisEvidenceCollectionListener adaptEvidenceListener(AnalysisExecutionListener listener) {
        return new AnalysisEvidenceCollectionListener() {
            @Override
            public void onProviderStarted(
                    pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider provider,
                    pl.mkn.incidenttracker.analysis.evidence.AnalysisContext context
            ) {
                listener.onProviderStarted(provider, context);
            }

            @Override
            public void onProviderCompleted(
                    pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider provider,
                    AnalysisEvidenceSection section,
                    pl.mkn.incidenttracker.analysis.evidence.AnalysisContext updatedContext
            ) {
                listener.onProviderCompleted(provider, section, updatedContext);
            }
        };
    }

}
