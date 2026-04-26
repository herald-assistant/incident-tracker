package pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotToolBudgetRegistryTest {

    @Test
    void shouldCreateAndRemoveSessionState() {
        var registry = new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties());

        var state = registry.registerSession("analysis-run-1");

        assertEquals("analysis-run-1", state.sessionId());
        assertEquals(1, registry.sessionCount());
        assertTrue(registry.state("analysis-run-1").isPresent());

        var snapshot = registry.unregisterSession("analysis-run-1");

        assertTrue(snapshot.isPresent());
        assertEquals("analysis-run-1", snapshot.get().sessionId());
        assertEquals(0, registry.sessionCount());
        assertTrue(registry.state("analysis-run-1").isEmpty());
    }
}
