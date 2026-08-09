package pl.mkn.tdw.features.changeverification.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationAiResponseParser;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysisProvider;
import pl.mkn.tdw.features.changeverification.ai.preparation.ChangeVerificationPromptPreparationService;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

@Service
@RequiredArgsConstructor
public class ChangeVerificationCopilotComplianceAnalysisProvider implements ChangeVerificationComplianceAnalysisProvider {

    private final ChangeVerificationPromptPreparationService promptPreparationService;
    private final ChangeVerificationCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final ChangeVerificationAiResponseParser responseParser;
    private final AnalysisAiAuthRefResolver authRefResolver;

    @Override
    public ChangeVerificationComplianceAnalysis analyze(
            String jobId,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        var authRef = authRefResolver.resolveForCurrentRequest();
        var preparation = promptPreparationService.prepare(request, sourceDiscovery);
        var runRequest = runRequestAssembler.assemble(
                jobId,
                request,
                sourceDiscovery,
                preparation,
                authRef != null ? authRef : AnalysisAiAuthRef.localToken(null)
        );
        var preparedSession = runPreparationService.prepare(runRequest);
        if (toolEvidenceListener != null && toolEvidenceListener != AnalysisAiToolEvidenceListener.NO_OP) {
            preparedSession = preparedSession.withEvidenceSink(toolEvidenceListener::onToolEvidenceUpdated);
        }
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            preparedSession = preparedSession.withActivitySink(activityListener::onAiActivity);
        }
        var executionResult = executionGateway.execute(preparedSession);
        return new ChangeVerificationComplianceAnalysis(
                responseParser.parse(executionResult.content()),
                executionResult.usage(),
                preparation.prompt(),
                executionResult.sessionId(),
                executionResult.report()
        );
    }
}
