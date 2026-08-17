package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryPromptPreparation;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryAssessmentCopilotRunRequestAssembler {

    private static final String DENIED_TOOL_MESSAGE =
            "This is a one-shot Delivery Effectiveness Assessment. Use only the inline prompt and return JSON.";

    private final CopilotRunAuthMapper runAuthMapper;

    public CopilotRunRequest assemble(
            String runReference,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef,
            DeliveryPromptPreparation preparation
    ) {
        var sessionConfig = new CopilotSessionConfigRequest(
                "delivery-effectiveness-" + UUID.randomUUID(),
                List.of(),
                List.of(),
                modelSelection(options),
                DENIED_TOOL_MESSAGE,
                false
        );
        return new CopilotRunRequest(
                runReference,
                runAuthMapper.toRunAuth(authRef),
                CopilotSessionTarget.newSession(),
                preparation.prompt(),
                sessionConfig,
                preparation.artifacts(),
                null
        );
    }

    private CopilotModelSelection modelSelection(AnalysisAiOptions options) {
        return options != null
                ? new CopilotModelSelection(options.model(), options.reasoningEffort())
                : CopilotModelSelection.DEFAULT;
    }
}
