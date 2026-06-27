package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentSessionConfigRequestFactoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldBuildIncidentSessionConfigFromPolicySkillsAndOptions() {
        var properties = new CopilotSdkProperties();
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
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
        assertSelectedSkillRoot(request.skillDirectories(), CopilotIncidentRuntimeSkillNames.allSkillNames());
        assertEquals("gpt-5.4", request.modelSelection().model());
        assertEquals("high", request.modelSelection().reasoningEffort());
        assertEquals(
                "Use only the inline incident artifacts and the explicitly enabled incident-analysis tools for this session.",
                request.deniedToolUseMessage()
        );
    }

    private void assertSelectedSkillRoot(List<String> skillDirectories, List<String> expectedSkillNames) {
        assertEquals(1, skillDirectories.size());
        var selectedRoot = Path.of(skillDirectories.get(0));
        assertTrue(Files.isDirectory(selectedRoot));
        for (var expectedSkillName : expectedSkillNames) {
            assertTrue(
                    Files.isRegularFile(selectedRoot.resolve(expectedSkillName).resolve("SKILL.md")),
                    () -> "Missing selected skill in root: " + expectedSkillName
            );
        }
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
