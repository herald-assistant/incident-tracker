package pl.mkn.tdw.features.incidentanalysis.flow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisProvider;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.evidence.*;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

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
        return analyze(correlationId, options, AnalysisAiAuthRef.localToken(null), listener);
    }

    public AnalysisExecution analyze(
            String correlationId,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef,
            AnalysisExecutionListener listener
    ) {
        var context = analysisEvidenceCollector.collect(correlationId, listener);

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
                options,
                authRef
        );

        listener.onAiStarted(aiRequest, context);
        try (var preparedAnalysis = initialAnalysisProvider.prepare(aiRequest)) {
            var preparedPrompt = preparedAnalysis.prompt();
            listener.onAiPromptPrepared(aiRequest, preparedPrompt, context);

            var aiResponse = initialAnalysisProvider.analyze(
                    preparedAnalysis,
                    listener::onAiToolEvidenceUpdated,
                    listener::onAiActivity
            );
            var result = new AnalysisResultResponse(
                    "COMPLETED",
                    correlationId,
                    aiRequest.environment(),
                    aiRequest.gitLabBranch(),
                    aiResponse.detectedProblem(),
                    aiResponse.affectedProcess(),
                    aiResponse.affectedBoundedContext(),
                    aiResponse.affectedTeam(),
                    aiResponse.functionalAnalysis(),
                    aiResponse.technicalAnalysis(),
                    aiResponse.confidence(),
                    aiResponse.visibilityLimits(),
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

}
