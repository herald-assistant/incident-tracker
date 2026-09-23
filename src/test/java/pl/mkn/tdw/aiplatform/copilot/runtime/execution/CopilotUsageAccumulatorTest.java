package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CopilotUsageAccumulatorTest {

    @Test
    void sumsPerCallNanoAiuAsCredits() {
        var accumulator = new CopilotUsageAccumulator();
        accumulator.recordAssistantUsage("model-a", 100, 20, 0, 0, 1_250_000_000L, 100);
        accumulator.recordAssistantUsage("model-b", 50, 10, 0, 0, 750_000_000L, 100);

        var usage = accumulator.snapshot();
        assertEquals(2D, usage.aiCredits());
        assertEquals(2, usage.apiCallCount());
        assertEquals("model-a, model-b", usage.model());
    }

    @Test
    void leavesCreditsUnknownWhenAnyCallOmitsNanoAiu() {
        var accumulator = new CopilotUsageAccumulator();
        accumulator.recordAssistantUsage("model-a", 100, 20, 0, 0, 1_250_000_000L, 100);
        accumulator.recordAssistantUsage("model-a", 50, 10, 0, 0, null, 100);

        assertNull(accumulator.snapshot().aiCredits());
    }
}
