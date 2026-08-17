package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAiResponseParser;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryPromptPreparation;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryUnitAiAnalysis;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryUnitAssessmentProvider;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

@Service
@RequiredArgsConstructor
public class DeliveryAssessmentCopilotProvider implements DeliveryUnitAssessmentProvider {

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
            DeliveryPromptPreparation preparation,
            AnalysisAiActivityListener activityListener
    ) {
        var request = runRequestAssembler.assemble(runReference, options, authRef, preparation);
        var session = runPreparationService.prepare(request);
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            session = session.withActivitySink(activityListener::onAiActivity);
        }
        var result = executionGateway.execute(session);
        var response = responseParser.parse(result.content());
        return new DeliveryUnitAiAnalysis(
                response,
                result.usage(),
                preparation.prompt(),
                result.sessionId()
        );
    }
}
