package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAiResponseParser;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryPromptPreparationService;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryUnitAiAnalysis;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryUnitAssessmentProvider;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

@Service
@RequiredArgsConstructor
public class DeliveryAssessmentCopilotProvider implements DeliveryUnitAssessmentProvider {

    private final DeliveryPromptPreparationService promptPreparationService;
    private final DeliveryAssessmentCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final DeliveryAiResponseParser responseParser;

    @Override
    public DeliveryUnitAiAnalysis analyze(
            String runReference,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef,
            DeliveryEvidencePacket packet,
            AnalysisAiActivityListener activityListener
    ) {
        var preparation = promptPreparationService.prepare(packet);
        var request = runRequestAssembler.assemble(runReference, options, authRef, packet, preparation);
        var session = runPreparationService.prepare(request);
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            session = session.withActivitySink(activityListener::onAiActivity);
        }
        var result = executionGateway.execute(session);
        return new DeliveryUnitAiAnalysis(
                responseParser.parse(result.content()),
                result.usage(),
                preparation.prompt(),
                result.sessionId(),
                result.report()
        );
    }
}
