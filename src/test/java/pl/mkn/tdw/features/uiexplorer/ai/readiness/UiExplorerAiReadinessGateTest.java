package pl.mkn.tdw.features.uiexplorer.ai.readiness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerAiReadinessGateTest {

    private final UiExplorerAiReadinessGate gate = new UiExplorerAiReadinessGate();

    @Test
    void shouldRequireScopedFallbackForPartialSyntheticCrmContext() {
        var readiness = gate.evaluate(request(), context());

        assertThat(readiness.status()).isEqualTo(UiExplorerAiReadinessStatus.PARTIAL);
        assertThat(readiness.fallbackToolsRequired()).isTrue();
        assertThat(readiness.activeSections()).hasSize(2);
        assertThat(readiness.limitations()).allSatisfy(value ->
                assertThat(value).containsIgnoringCase("runtime")
        );
    }

    @Test
    void shouldBlockWithoutDeterministicContext() {
        var readiness = gate.evaluate(request(), null);

        assertThat(readiness.status()).isEqualTo(UiExplorerAiReadinessStatus.BLOCKED);
        assertThat(readiness.executable()).isFalse();
        assertThat(readiness.fallbackToolsRequired()).isFalse();
    }
}
