package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentSessionConfigRequestFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolAccessPolicy;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiOptions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopilotIncidentSessionConfigRequestFactoryTest {

    @Test
    void shouldBuildIncidentSessionConfigFromPolicySkillsAndOptions() {
        var properties = new CopilotSdkProperties();
        properties.setSkillResourceRoots(List.of());
        properties.setSkillDirectories(List.of("skills/incident-analysis"));
        var tool = tool("gitlab_find_flow_context");
        var policy = new CopilotIncidentToolAccessPolicy(
                List.of(tool),
                List.of(tool.name()),
                true,
                false,
                true,
                false,
                null
        );

        var factory = new CopilotIncidentSessionConfigRequestFactory(new CopilotSkillRuntimeLoader(properties));

        var request = factory.create(
                "analysis-123",
                policy,
                new AnalysisAiOptions(" gpt-5.4 ", " high ")
        );

        assertEquals("analysis-123", request.sessionId());
        assertEquals(List.of(tool), request.tools());
        assertEquals(List.of("gitlab_find_flow_context"), request.availableToolNames());
        assertEquals(List.of("skills/incident-analysis"), request.skillDirectories());
        assertEquals("gpt-5.4", request.modelSelection().model());
        assertEquals("high", request.modelSelection().reasoningEffort());
        assertEquals(
                "Use only the inline incident artifacts and the explicitly enabled incident-analysis tools for this session.",
                request.deniedToolUseMessage()
        );
    }

    private ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }
}
