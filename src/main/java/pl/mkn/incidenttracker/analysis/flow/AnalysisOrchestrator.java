package pl.mkn.incidenttracker.analysis.flow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.AnalysisFlowDiagram;
import pl.mkn.incidenttracker.analysis.AnalysisMode;
import pl.mkn.incidenttracker.analysis.AnalysisProblemNature;
import pl.mkn.incidenttracker.analysis.AnalysisVariantStatus;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiPriorResult;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollectionListener;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProviderDescriptor;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.exploratory.ExploratoryAnalysisProperties;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final AnalysisEvidenceCollector analysisEvidenceCollector;
    private final AnalysisAiProvider analysisAiProvider;
    private final GitLabProperties gitLabProperties;
    private final ExploratoryAnalysisProperties exploratoryProperties;

    public AnalysisExecution analyze(String correlationId) {
        return analyze(correlationId, AnalysisExecutionListener.NO_OP);
    }

    public AnalysisExecution analyze(String correlationId, AnalysisExecutionListener listener) {
        var baseContext = analysisEvidenceCollector.collect(correlationId, adaptEvidenceListener(listener));

        if (!baseContext.hasAnyEvidence()) {
            throw new AnalysisDataNotFoundException(correlationId);
        }

        var deploymentContext = DeploymentContextEvidenceView.from(baseContext);
        var conservativeRequest = new AnalysisAiAnalysisRequest(
                correlationId,
                deploymentContext.environment(),
                deploymentContext.gitLabBranch(),
                gitLabProperties.getGroup(),
                AnalysisMode.CONSERVATIVE,
                baseContext.evidenceSections()
        );
        var conservativeResponse = runAi(conservativeRequest, baseContext, listener);
        var conservativeVariant = toCompletedVariant(AnalysisMode.CONSERVATIVE, conservativeResponse);
        var exploratoryVariant = disabledVariant();

        if (exploratoryProperties.isEnabled()) {
            try {
                var exploratoryRequest = new AnalysisAiAnalysisRequest(
                        correlationId,
                        deploymentContext.environment(),
                        deploymentContext.gitLabBranch(),
                        gitLabProperties.getGroup(),
                        AnalysisMode.EXPLORATORY,
                        baseContext.evidenceSections(),
                        toPriorResult(conservativeResponse)
                );
                var exploratoryResponse = runAi(exploratoryRequest, baseContext, listener);
                exploratoryVariant = toCompletedVariant(AnalysisMode.EXPLORATORY, exploratoryResponse);
            } catch (RuntimeException exception) {
                exploratoryVariant = failedVariant(exception.getMessage());
            }
        }

        var result = new AnalysisResultResponse(
                "COMPLETED",
                correlationId,
                conservativeRequest.environment(),
                conservativeRequest.gitLabBranch(),
                new AnalysisResultVariants(
                        conservativeVariant,
                        exploratoryVariant
                )
        );

        return new AnalysisExecution(baseContext, result);
    }

    public List<AnalysisEvidenceProviderDescriptor> providerDescriptors() {
        return analysisEvidenceCollector.providerDescriptors();
    }

    public boolean exploratoryEnabled() {
        return exploratoryProperties.isEnabled();
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

            @Override
            public void onProviderFailed(
                    pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider provider,
                    RuntimeException exception,
                    pl.mkn.incidenttracker.analysis.evidence.AnalysisContext context
            ) {
                listener.onProviderFailed(provider, exception, context);
            }
        };
    }

    private AnalysisAiAnalysisResponse runAi(
            AnalysisAiAnalysisRequest request,
            pl.mkn.incidenttracker.analysis.evidence.AnalysisContext context,
            AnalysisExecutionListener listener
    ) {
        listener.onAiStarted(request, context);
        var preparedPrompt = analysisAiProvider.preparePrompt(request);
        listener.onAiPromptPrepared(request, preparedPrompt, context);

        try {
            var response = analysisAiProvider.analyze(request);
            listener.onAiCompleted(request, response, context);
            return response;
        } catch (RuntimeException exception) {
            listener.onAiFailed(request, exception, context);
            throw exception;
        }
    }

    private AnalysisVariantResultResponse toCompletedVariant(
            AnalysisMode mode,
            AnalysisAiAnalysisResponse aiResponse
    ) {
        return new AnalysisVariantResultResponse(
                mode,
                AnalysisVariantStatus.COMPLETED,
                aiResponse.detectedProblem(),
                aiResponse.summary(),
                aiResponse.recommendedAction(),
                aiResponse.rationale(),
                aiResponse.problemNature(),
                aiResponse.confidence(),
                aiResponse.prompt(),
                aiResponse.diagram()
        );
    }

    private AnalysisVariantResultResponse disabledVariant() {
        return new AnalysisVariantResultResponse(
                AnalysisMode.EXPLORATORY,
                AnalysisVariantStatus.DISABLED,
                "",
                "",
                "",
                "Tryb exploratory jest wyłączony w konfiguracji aplikacji.",
                AnalysisProblemNature.HYPOTHESIS,
                null,
                null,
                null
        );
    }

    private AnalysisVariantResultResponse failedVariant(String message) {
        return new AnalysisVariantResultResponse(
                AnalysisMode.EXPLORATORY,
                AnalysisVariantStatus.FAILED,
                "",
                "",
                "",
                message != null ? message : "Tryb exploratory zakończył się błędem.",
                AnalysisProblemNature.HYPOTHESIS,
                null,
                null,
                null
        );
    }

    private AnalysisAiPriorResult toPriorResult(AnalysisAiAnalysisResponse response) {
        return new AnalysisAiPriorResult(
                response.detectedProblem(),
                response.summary(),
                response.recommendedAction(),
                response.rationale(),
                response.problemNature(),
                response.confidence()
        );
    }

}
