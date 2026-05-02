package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentHiddenToolContextFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolSessionContextFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentToolSessionContextFactoryTest {

    private final CopilotIncidentToolSessionContextFactory factory =
            new CopilotIncidentToolSessionContextFactory(new CopilotIncidentHiddenToolContextFactory());

    @Test
    void shouldCreateInitialAnalysisSessionContextWithIncidentScope() {
        var request = new InitialAnalysisRequest(
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime",
                List.of()
        );

        var context = factory.fromInitialRequest(request);

        assertTrue(context.copilotSessionId().startsWith("analysis-"));
        assertEquals("analysis-" + context.analysisRunId(), context.copilotSessionId());
        assertEquals("corr-123", context.hiddenContext().get(AgentToolContextKeys.CORRELATION_ID));
        assertEquals("zt01", context.hiddenContext().get(AgentToolContextKeys.ENVIRONMENT));
        assertEquals("release/2026.04", context.hiddenContext().get(AgentToolContextKeys.GITLAB_BRANCH));
        assertEquals("sample/runtime", context.hiddenContext().get(AgentToolContextKeys.GITLAB_GROUP));
        assertEquals(context.analysisRunId(), context.hiddenContext().get(AgentToolContextKeys.ANALYSIS_RUN_ID));
        assertEquals(context.copilotSessionId(), context.hiddenContext().get(AgentToolContextKeys.COPILOT_SESSION_ID));
    }

    @Test
    void shouldCreateFollowUpSessionContextWithChatPrefix() {
        var request = new AnalysisAiChatRequest(
                "corr-chat",
                "prod",
                "main",
                "sample/runtime",
                List.of(),
                List.of(),
                null,
                List.of(),
                "What next?",
                null
        );

        var context = factory.fromChatRequest(request);

        assertTrue(context.copilotSessionId().startsWith("analysis-chat-"));
        assertEquals("analysis-chat-" + context.analysisRunId(), context.copilotSessionId());
        assertEquals("corr-chat", context.hiddenContext().get(AgentToolContextKeys.CORRELATION_ID));
        assertEquals("prod", context.hiddenContext().get(AgentToolContextKeys.ENVIRONMENT));
        assertEquals("main", context.hiddenContext().get(AgentToolContextKeys.GITLAB_BRANCH));
        assertEquals("sample/runtime", context.hiddenContext().get(AgentToolContextKeys.GITLAB_GROUP));
    }
}
