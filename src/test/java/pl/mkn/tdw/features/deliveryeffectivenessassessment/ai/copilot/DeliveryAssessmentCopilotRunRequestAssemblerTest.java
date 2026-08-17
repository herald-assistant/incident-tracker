package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryPromptPreparation;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAssessmentCopilotRunRequestAssemblerTest {

    @Test
    void shouldPrepareOneShotSessionWithoutFeatureToolsOrMutableReport() {
        var assembler = new DeliveryAssessmentCopilotRunRequestAssembler(new CopilotRunAuthMapper());
        var request = assembler.assemble(
                "job-1:DU-CRM-1",
                new AnalysisAiOptions("gpt-5.4-mini", "high"),
                AnalysisAiAuthRef.localToken("test"),
                new DeliveryPromptPreparation(
                        "effective skill + source artifacts + result contract",
                        Map.of("delivery-effectiveness/issues.md", "CRM-1")
                )
        );

        assertThat(request.prompt()).isEqualTo("effective skill + source artifacts + result contract");
        assertThat(request.sessionConfigRequest().tools()).isEmpty();
        assertThat(request.sessionConfigRequest().availableToolNames()).isEmpty();
        assertThat(request.sessionConfigRequest().effectiveAvailableToolNames()).isEmpty();
        assertThat(request.sessionConfigRequest().skillToolAvailable()).isFalse();
        assertThat(request.initialReport()).isNull();
        assertThat(request.artifactContents()).containsEntry("delivery-effectiveness/issues.md", "CRM-1");
    }
}
