package pl.mkn.tdw.features.uxinspector.ai.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.targetContext;

class UxInspectorTargetToolsTest {

    @Test
    void shouldIssueOpaqueSessionBoundReferenceAndRejectReplay() {
        var tools = new UxInspectorTargetTools("run-crm", targetContext());
        var context = context("run-crm");

        var listed = tools.listTargetCandidates("Zweryfikuj target", context);
        var targetRef = listed.candidates().get(0).targetRef();
        var first = tools.readTargetSlice(targetRef, "Odczytaj waski slice", context);
        var replay = tools.readTargetSlice(targetRef, "Powtorz", context);

        assertThat(targetRef).startsWith("uxi_").doesNotContain("contact-create");
        assertThat(first.status()).isEqualTo("ok");
        assertThat(first.sourceSlice()).contains("FILE ");
        assertThat(replay.status()).isEqualTo("rejected");
        assertThat(replay.sourceSlice()).isEmpty();
    }

    @Test
    void shouldRejectUnknownReferenceAndCrossSessionCallsWithoutAFeatureCallLimit() {
        var tools = new UxInspectorTargetTools("run-crm", targetContext());

        assertThat(tools.readTargetSlice("uxi_unknown", "Probe", context("run-crm")).status())
                .isEqualTo("rejected");
        assertThatThrownBy(() -> tools.listTargetCandidates("Obcy run", context("run-other")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("scope mismatch");

        var repeated = new UxInspectorTargetTools("run-repeated", targetContext());
        for (var index = 0; index < 12; index++) {
            assertThat(repeated.listTargetCandidates("Call " + index, context("run-repeated")).status()).isEqualTo("ok");
        }
    }

    private ToolContext context(String runId) {
        return new ToolContext(Map.of(AgentToolContextKeys.ANALYSIS_RUN_ID, runId));
    }
}
