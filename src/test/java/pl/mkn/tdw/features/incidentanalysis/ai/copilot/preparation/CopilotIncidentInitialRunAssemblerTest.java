package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

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

    private static final CopilotToolDescriptionContext INCIDENT_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");

    @Test
    void shouldAssembleRunRequestForInitialAnalysis() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var toolSessionContextFactory = mock(CopilotIncidentToolSessionContextFactory.class);
        var sessionConfigRequestFactory = mock(CopilotIncidentSessionConfigRequestFactory.class);
        var artifactService = mock(CopilotIncidentArtifactService.class);
        var toolAccessPolicyFactory = mock(CopilotIncidentToolAccessPolicyFactory.class);
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
        var toolAccessPolicy = CopilotIncidentToolAccessPolicy.empty();
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
        when(toolFactory.createToolDefinitions(toolSessionContext, INCIDENT_DESCRIPTION_CONTEXT)).thenReturn(List.of());
        when(toolAccessPolicyFactory.create(eq(request), anyList())).thenReturn(toolAccessPolicy);
        when(sessionConfigRequestFactory.create(toolSessionContext.copilotSessionId(), toolAccessPolicy, options))
                .thenReturn(sessionConfigRequest);
        when(artifactService.renderArtifacts(request, toolAccessPolicy, sessionConfigRequest)).thenReturn(artifacts);
        when(promptRenderer.render(request, toolAccessPolicy, sessionConfigRequest, artifacts)).thenReturn("Initial prompt");

        var assembly = assembler.assemble(request);
        var runRequest = assembly.runRequest();

        assertEquals("corr-123", runRequest.runReference());
        assertEquals("Initial prompt", runRequest.prompt());
        assertSame(sessionConfigRequest, runRequest.sessionConfigRequest());
        assertEquals(Map.of("01-incident-digest.md", "# Digest"), runRequest.artifactContents());
        verify(sessionConfigRequestFactory).create(toolSessionContext.copilotSessionId(), toolAccessPolicy, options);
        verify(artifactService).renderArtifacts(request, toolAccessPolicy, sessionConfigRequest);
        verify(promptRenderer).render(request, toolAccessPolicy, sessionConfigRequest, artifacts);
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
