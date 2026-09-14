package pl.mkn.tdw.features.operationalcontextassistance.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraftValidationTools;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

@Service
@RequiredArgsConstructor
public class OperationalContextAssistanceCopilotProvider {

    private final OperationalContextAssistanceCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;

    public OperationalContextAssistanceCopilotResult execute(
            String runReference,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef,
            OperationalContextAssistancePromptPreparation preparation,
            OperationalContextGitLabSourceSnapshot source,
            OperationalContextAssistanceDraftValidationTools.ValidationSession validationSession,
            AnalysisAiActivityListener activityListener
    ) {
        var assembly = runRequestAssembler.assemble(runReference, options, authRef, preparation, source,
                validationSession);
        var session = runPreparationService.prepare(assembly.runRequest());
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            session = session.withActivitySink(activityListener::onAiActivity);
        }
        var result = executionGateway.execute(session);
        return new OperationalContextAssistanceCopilotResult(
                result,
                assembly.sourceScope() != null ? assembly.sourceScope().readSourceRefs() : java.util.Set.of()
        );
    }
}
