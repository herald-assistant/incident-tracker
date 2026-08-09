package pl.mkn.tdw.features.configdriftviewer.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.configdriftviewer.ai.copilot.ConfigDriftViewerCopilotRunRequestAssembler;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation.ConfigDriftViewerPromptPreparation;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

@Component
@RequiredArgsConstructor
public class ConfigDriftViewerCopilotAiRunner implements ConfigDriftViewerAiRunner {

    private final ConfigDriftViewerCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final ConfigDriftViewerAiAssessmentService assessmentService;

    @Override
    public ConfigDriftViewerAiRunResult run(
            String runReference,
            ConfigDriftViewerJobStartRequest request,
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerDeepContext deepContext,
            ConfigDriftViewerPromptPreparation preparation,
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
                deterministic,
                deepContext,
                assembly.runRequest().initialReport(),
                execution.report()
        );
        return new ConfigDriftViewerAiRunResult(assessment, execution.usage());
    }
}
