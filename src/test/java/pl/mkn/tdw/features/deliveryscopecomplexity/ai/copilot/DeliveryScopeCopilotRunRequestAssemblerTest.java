package pl.mkn.tdw.features.deliveryscopecomplexity.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryPromptPreparation;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryScopeCopilotRunRequestAssemblerTest {

    @Test
    void shouldPrepareOneShotSessionWithoutFeatureToolsOrMutableReport() {
        var assembler = new DeliveryScopeCopilotRunRequestAssembler(new CopilotRunAuthMapper());
        var request = assembler.assemble(
                "job-1:DU-CRM-1",
                new AnalysisAiOptions("gpt-5.4-mini", "high"),
                AnalysisAiAuthRef.localToken("test"),
                new DeliveryPromptPreparation(
                        "effective skill + source artifacts + result contract",
                        Map.of("delivery-scope-complexity/issues.md", "CRM-1")
                )
        );

        assertThat(request.prompt()).isEqualTo("effective skill + source artifacts + result contract");
        assertThat(request.sessionConfigRequest().tools()).isEmpty();
        assertThat(request.sessionConfigRequest().availableToolNames()).isEmpty();
        assertThat(request.sessionConfigRequest().effectiveAvailableToolNames()).isEmpty();
        assertThat(request.sessionConfigRequest().skillToolAvailable()).isFalse();
        assertThat(request.initialReport()).isNull();
        assertThat(request.artifactContents()).containsEntry("delivery-scope-complexity/issues.md", "CRM-1");
    }

    @Test
    void shouldKeepReasoningEffortEmptyForExplicitModel() {
        var assembler = new DeliveryScopeCopilotRunRequestAssembler(new CopilotRunAuthMapper());

        var request = assembler.assemble(
                "job-1:DU-CRM-1",
                new AnalysisAiOptions("gpt-basic", null),
                AnalysisAiAuthRef.localToken("test"),
                new DeliveryPromptPreparation("prompt", Map.of())
        );

        assertThat(request.sessionConfigRequest().modelSelection().model()).isEqualTo("gpt-basic");
        assertThat(request.sessionConfigRequest().modelSelection().reasoningEffort()).isNull();
    }
}
