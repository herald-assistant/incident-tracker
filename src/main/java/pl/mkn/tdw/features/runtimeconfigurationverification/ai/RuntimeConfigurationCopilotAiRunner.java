package pl.mkn.tdw.features.runtimeconfigurationverification.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot.RuntimeConfigurationCopilotRunRequestAssembler;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparation;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

@Component
@RequiredArgsConstructor
public class RuntimeConfigurationCopilotAiRunner implements RuntimeConfigurationAiRunner {

    private final RuntimeConfigurationCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final RuntimeConfigurationAiAssessmentService assessmentService;

    @Override
    public RuntimeConfigurationAiRunResult run(
            String runReference,
            RuntimeConfigurationVerificationJobStartRequest request,
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deepContext,
            RuntimeConfigurationPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            AnalysisAiToolEvidenceListener evidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        var assembly = runRequestAssembler.assemble(
                runReference,
                request,
                deterministic,
                deepContext,
                preparation,
                authRef
        );
        var preparedSession = runPreparationService.prepare(assembly.runRequest())
                .withEvidenceSink(evidenceListener != null ? evidenceListener::onToolEvidenceUpdated : null)
                .withActivitySink(activityListener != null ? activityListener::onAiActivity : null);
        var execution = executionGateway.execute(preparedSession);
        var assessment = assessmentService.assess(
                execution.content(),
                request.mode(),
                deterministic,
                deepContext,
                assembly.runRequest().initialReport(),
                execution.report()
        );
        return new RuntimeConfigurationAiRunResult(assessment, execution.usage());
    }
}
