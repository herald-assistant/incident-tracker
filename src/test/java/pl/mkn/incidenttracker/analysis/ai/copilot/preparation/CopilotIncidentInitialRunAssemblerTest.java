package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.options.AnalysisAiOptions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotIncidentInitialRunAssemblerTest {

    @Test
    void shouldAssembleRunRequestAndMetricsSnapshotForInitialAnalysis() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var toolSessionContextFactory = mock(CopilotIncidentToolSessionContextFactory.class);
        var sessionConfigRequestFactory = mock(CopilotIncidentSessionConfigRequestFactory.class);
        var artifactService = mock(CopilotIncidentArtifactService.class);
        var toolAccessPolicyFactory = mock(CopilotToolAccessPolicyFactory.class);
        var promptRenderer = mock(CopilotIncidentPromptRenderer.class);
        var runRequestFactory = new CopilotIncidentRunRequestFactory(new CopilotArtifactContentMapper());
        var assembler = new CopilotIncidentInitialRunAssembler(
                toolFactory,
                toolSessionContextFactory,
                sessionConfigRequestFactory,
                artifactService,
                toolAccessPolicyFactory,
                promptRenderer,
                runRequestFactory
        );

        var options = new AnalysisAiOptions("gpt-5.4", "medium");
        var request = new InitialAnalysisRequest(
                "corr-123",
                "dev",
                "release/2026.04",
                "sample/group",
                List.of(),
                options
        );
        var toolSessionContext = new CopilotToolSessionContext(
                "run-123",
                "analysis-run-123",
                Map.of()
        );
        var toolAccessPolicy = CopilotToolAccessPolicy.empty();
        var artifacts = List.of(artifact("01-incident-digest.md", "# Digest"));
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                "analysis-run-123",
                List.of(),
                List.of(),
                List.of("copilot-skills/incident"),
                null,
                "Denied"
        );

        when(toolSessionContextFactory.fromInitialRequest(request)).thenReturn(toolSessionContext);
        when(toolFactory.createToolDefinitions(toolSessionContext)).thenReturn(List.of());
        when(toolAccessPolicyFactory.create(eq(request), anyList())).thenReturn(toolAccessPolicy);
        when(artifactService.renderArtifacts(request, toolAccessPolicy)).thenReturn(artifacts);
        when(promptRenderer.render(request, toolAccessPolicy, artifacts)).thenReturn("Initial prompt");
        when(sessionConfigRequestFactory.create(toolSessionContext.copilotSessionId(), toolAccessPolicy, options))
                .thenReturn(sessionConfigRequest);

        var assembly = assembler.assemble(request);
        var runRequest = assembly.runRequest();
        var metrics = assembly.metrics();

        assertEquals("corr-123", runRequest.runReference());
        assertEquals("Initial prompt", runRequest.prompt());
        assertSame(sessionConfigRequest, runRequest.sessionConfigRequest());
        assertEquals(Map.of("01-incident-digest.md", "# Digest"), runRequest.artifactContents());
        assertSame(toolSessionContext, metrics.toolSessionContext());
        assertEquals(artifacts, metrics.renderedArtifacts());
        verify(sessionConfigRequestFactory).create(toolSessionContext.copilotSessionId(), toolAccessPolicy, options);
        verify(promptRenderer).render(request, toolAccessPolicy, artifacts);
    }

    private CopilotRenderedArtifact artifact(String displayName, String content) {
        return new CopilotRenderedArtifact(
                displayName,
                "test",
                "copilot",
                "test",
                1,
                "text/markdown",
                content
        );
    }
}
