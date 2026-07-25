package pl.mkn.tdw.features.changeverification.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationSmokePackAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationSmokePackAnalysisProvider;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationSmokePackResponseParser;
import pl.mkn.tdw.features.changeverification.ai.preparation.ChangeVerificationSmokePackPromptPreparationService;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

@Service
@RequiredArgsConstructor
public class ChangeVerificationCopilotSmokePackAnalysisProvider implements ChangeVerificationSmokePackAnalysisProvider {

    private final ChangeVerificationSmokePackPromptPreparationService promptPreparationService;
    private final ChangeVerificationCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final ChangeVerificationSmokePackResponseParser responseParser;
    private final AnalysisAiAuthRefResolver authRefResolver;

    @Override
    public ChangeVerificationSmokePackAnalysis analyze(
            String jobId,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis
    ) {
        return analyze(jobId, request, sourceDiscovery, complianceAnalysis, AnalysisAiToolEvidenceListener.NO_OP);
    }

    @Override
    public ChangeVerificationSmokePackAnalysis analyze(
            String jobId,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        return analyze(jobId, request, sourceDiscovery, complianceAnalysis, toolEvidenceListener, AnalysisAiActivityListener.NO_OP);
    }

    @Override
    public ChangeVerificationSmokePackAnalysis analyze(
            String jobId,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        var authRef = authRefResolver.resolveForCurrentRequest();
        var preparation = promptPreparationService.prepare(request, sourceDiscovery, complianceAnalysis);
        var runRequest = runRequestAssembler.assemble(
                jobId + "-smoke",
                request,
                sourceDiscovery,
                preparation,
                authRef != null ? authRef : AnalysisAiAuthRef.localToken(null),
                ChangeVerificationCopilotRuntimeSkillNames.smokePackSkillNames(),
                ChangeVerificationCopilotToolContextKeys.RUN_KIND_SMOKE_PACK
        );
        var preparedSession = runPreparationService.prepare(runRequest);
        if (toolEvidenceListener != null && toolEvidenceListener != AnalysisAiToolEvidenceListener.NO_OP) {
            preparedSession = preparedSession.withEvidenceSink(toolEvidenceListener::onToolEvidenceUpdated);
        }
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            preparedSession = preparedSession.withActivitySink(activityListener::onAiActivity);
        }
        var executionResult = executionGateway.execute(preparedSession);
        return new ChangeVerificationSmokePackAnalysis(
                responseParser.parse(executionResult.content()),
                executionResult.usage(),
                preparation.prompt(),
                executionResult.sessionId()
        );
    }
}
