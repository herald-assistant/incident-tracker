package pl.mkn.incidenttracker.features.incidentanalysis.flow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.evidence.*;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.options.AnalysisAiOptions;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final AnalysisEvidenceCollector analysisEvidenceCollector;
    private final InitialAnalysisProvider initialAnalysisProvider;
    private final GitLabProperties gitLabProperties;

    public AnalysisExecution analyze(String correlationId) {
        return analyze(correlationId, AnalysisExecutionListener.NO_OP);
    }

    public AnalysisExecution analyze(String correlationId, AnalysisExecutionListener listener) {
        return analyze(correlationId, AnalysisAiOptions.DEFAULT, listener);
    }

    public AnalysisExecution analyze(
            String correlationId,
            AnalysisAiOptions options,
            AnalysisExecutionListener listener
    ) {
        var context = analysisEvidenceCollector.collect(correlationId, adaptEvidenceListener(listener));

        if (!context.hasAnyEvidence()) {
            throw new AnalysisDataNotFoundException(correlationId);
        }

        var deploymentContext = DeploymentContextEvidenceView.from(context);
        var aiRequest = new InitialAnalysisRequest(
                correlationId,
                deploymentContext.environment(),
                deploymentContext.gitLabBranch(),
                gitLabProperties.getGroup(),
                context.evidenceSections(),
                options
        );

        listener.onAiStarted(aiRequest, context);
        try (var preparedAnalysis = initialAnalysisProvider.prepare(aiRequest)) {
            var preparedPrompt = preparedAnalysis.prompt();
            listener.onAiPromptPrepared(aiRequest, preparedPrompt, context);

            var aiResponse = initialAnalysisProvider.analyze(
                    preparedAnalysis,
                    listener::onAiToolEvidenceUpdated
            );
            var result = new AnalysisResultResponse(
                    "COMPLETED",
                    correlationId,
                    aiRequest.environment(),
                    aiRequest.gitLabBranch(),
                    aiResponse.summary(),
                    aiResponse.detectedProblem(),
                    aiResponse.recommendedAction(),
                    aiResponse.rationale(),
                    aiResponse.affectedFunction(),
                    aiResponse.affectedProcess(),
                    aiResponse.affectedBoundedContext(),
                    aiResponse.affectedTeam(),
                    aiResponse.prompt(),
                    aiResponse.usage()
            );

            return new AnalysisExecution(
                    context,
                    aiRequest,
                    StringUtils.hasText(preparedPrompt) ? preparedPrompt : aiResponse.prompt(),
                    aiResponse,
                    result
            );
        }
    }

    public List<AnalysisEvidenceProviderDescriptor> providerDescriptors() {
        return analysisEvidenceCollector.providerDescriptors();
    }

    private AnalysisEvidenceCollectionListener adaptEvidenceListener(AnalysisExecutionListener listener) {
        return new AnalysisEvidenceCollectionListener() {
            @Override
            public void onProviderStarted(AnalysisEvidenceProvider provider, AnalysisContext context) {
                listener.onProviderStarted(provider, context);
            }

            @Override
            public void onProviderCompleted(AnalysisEvidenceProvider provider, AnalysisEvidenceSection section, AnalysisContext updatedContext) {
                listener.onProviderCompleted(provider, section, updatedContext);
            }
        };
    }

}
