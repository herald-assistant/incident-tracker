package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CopilotIncidentFollowUpRunAssemblerTest {

    private static final CopilotToolDescriptionContext INCIDENT_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");

    @Test
    void shouldAssembleNeutralRunRequestForFollowUpChat() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var toolSessionContextFactory = mock(CopilotIncidentToolSessionContextFactory.class);
        var sessionConfigRequestFactory = mock(CopilotIncidentSessionConfigRequestFactory.class);
        var toolAccessPolicyFactory = mock(CopilotIncidentToolAccessPolicyFactory.class);
        var runRequestFactory = new CopilotIncidentRunRequestFactory(new CopilotArtifactContentMapper());
        var assembler = new CopilotIncidentFollowUpRunAssembler(
                toolFactory,
                toolSessionContextFactory,
                sessionConfigRequestFactory,
                toolAccessPolicyFactory,
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
                "copilot-session-123",
                options
        );
        var toolSessionContext = new CopilotToolSessionContext(
                "run-123",
                "copilot-session-123",
                Map.of()
        );
        var toolAccessPolicy = CopilotIncidentToolAccessPolicy.empty();
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                "copilot-session-123",
                List.of(),
                List.of(),
                List.of("copilot-skills/incident"),
                null,
                "Denied"
        );

        when(toolSessionContextFactory.fromChatRequest(request)).thenReturn(toolSessionContext);
        when(toolFactory.createToolDefinitions(toolSessionContext, INCIDENT_DESCRIPTION_CONTEXT)).thenReturn(List.of());
        when(toolAccessPolicyFactory.createForFollowUp(eq(request), anyList())).thenReturn(toolAccessPolicy);
        when(sessionConfigRequestFactory.create(toolSessionContext.copilotSessionId(), toolAccessPolicy, options))
                .thenReturn(sessionConfigRequest);

        var runRequest = assembler.assemble(request);

        assertEquals("corr-123", runRequest.runReference());
        assertEquals(CopilotSessionTarget.Type.EXISTING, runRequest.sessionTarget().type());
        assertEquals("copilot-session-123", runRequest.sessionTarget().sessionId());
        assertEquals("Co dalej?", runRequest.prompt());
        assertSame(sessionConfigRequest, runRequest.sessionConfigRequest());
        assertEquals(Map.of(), runRequest.artifactContents());
        verify(sessionConfigRequestFactory).create(toolSessionContext.copilotSessionId(), toolAccessPolicy, options);
    }

    @Test
    void shouldRejectFollowUpWithoutCopilotSessionId() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var toolSessionContextFactory = mock(CopilotIncidentToolSessionContextFactory.class);
        var sessionConfigRequestFactory = mock(CopilotIncidentSessionConfigRequestFactory.class);
        var toolAccessPolicyFactory = mock(CopilotIncidentToolAccessPolicyFactory.class);
        var runRequestFactory = new CopilotIncidentRunRequestFactory(new CopilotArtifactContentMapper());
        var assembler = new CopilotIncidentFollowUpRunAssembler(
                toolFactory,
                toolSessionContextFactory,
                sessionConfigRequestFactory,
                toolAccessPolicyFactory,
                runRequestFactory
        );
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
                AnalysisAiOptions.DEFAULT
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> assembler.assemble(request));

        assertEquals("Copilot follow-up requires copilotSessionId for session resume.", exception.getMessage());
        verifyNoInteractions(
                toolFactory,
                toolSessionContextFactory,
                sessionConfigRequestFactory,
                toolAccessPolicyFactory
        );
    }
}
