package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
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

class CopilotIncidentFollowUpRunAssemblerTest {

    @Test
    void shouldAssembleNeutralRunRequestForFollowUpChat() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var toolSessionContextFactory = mock(CopilotIncidentToolSessionContextFactory.class);
        var sessionConfigRequestFactory = mock(CopilotIncidentSessionConfigRequestFactory.class);
        var toolAccessPolicyFactory = mock(CopilotIncidentToolAccessPolicyFactory.class);
        var artifactRequestFactory = mock(CopilotFollowUpArtifactRequestFactory.class);
        var artifactService = mock(CopilotIncidentArtifactService.class);
        var promptRenderer = mock(CopilotIncidentFollowUpPromptRenderer.class);
        var runRequestFactory = new CopilotIncidentRunRequestFactory(new CopilotArtifactContentMapper());
        var assembler = new CopilotIncidentFollowUpRunAssembler(
                toolFactory,
                toolSessionContextFactory,
                sessionConfigRequestFactory,
                toolAccessPolicyFactory,
                artifactRequestFactory,
                artifactService,
                promptRenderer,
                runRequestFactory
        );

        var options = new AnalysisAiOptions("gpt-5.4", "medium");
        var request = new AnalysisAiChatRequest(
                "corr-123",
                "dev",
                "release/2026.04",
                "sample/group",
                List.of(),
                List.of(),
                null,
                List.of(),
                "Co dalej?",
                options
        );
        var toolSessionContext = new CopilotToolSessionContext(
                "run-123",
                "analysis-chat-run-123",
                Map.of()
        );
        var toolAccessPolicy = CopilotIncidentToolAccessPolicy.empty();
        var artifactRequest = new InitialAnalysisRequest(
                "corr-123",
                "dev",
                "release/2026.04",
                "sample/group",
                List.of(),
                options
        );
        var artifacts = List.of(artifact("01-incident-digest.md", "# Digest"));
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                "analysis-chat-run-123",
                List.of(),
                List.of(),
                List.of("copilot-skills/incident"),
                null,
                "Denied"
        );

        when(toolSessionContextFactory.fromChatRequest(request)).thenReturn(toolSessionContext);
        when(toolFactory.createToolDefinitions(toolSessionContext)).thenReturn(List.of());
        when(toolAccessPolicyFactory.createForFollowUp(eq(request), anyList())).thenReturn(toolAccessPolicy);
        when(artifactRequestFactory.create(request)).thenReturn(artifactRequest);
        when(artifactService.renderArtifacts(artifactRequest, toolAccessPolicy)).thenReturn(artifacts);
        when(promptRenderer.render(request, toolAccessPolicy, artifacts)).thenReturn("Follow-up prompt");
        when(sessionConfigRequestFactory.create(toolSessionContext.copilotSessionId(), toolAccessPolicy, options))
                .thenReturn(sessionConfigRequest);

        var runRequest = assembler.assemble(request);

        assertEquals("corr-123", runRequest.runReference());
        assertEquals("Follow-up prompt", runRequest.prompt());
        assertSame(sessionConfigRequest, runRequest.sessionConfigRequest());
        assertEquals(Map.of("01-incident-digest.md", "# Digest"), runRequest.artifactContents());
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
