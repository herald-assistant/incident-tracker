package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentHiddenToolContextFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolSessionContextFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
                "CRM/runtime",
                List.of()
        );

        var context = factory.fromInitialRequest(request);

        assertTrue(context.copilotSessionId().startsWith("analysis-"));
        assertEquals("analysis-" + context.analysisRunId(), context.copilotSessionId());
        assertEquals("corr-123", context.hiddenContext().get(AgentToolContextKeys.CORRELATION_ID));
        assertEquals("zt01", context.hiddenContext().get(AgentToolContextKeys.ENVIRONMENT));
        assertInstanceOf(String.class, context.hiddenContext().get(AgentToolContextKeys.REPORT_ID));
        assertTrue(((String) context.hiddenContext().get(AgentToolContextKeys.REPORT_ID)).startsWith("report-"));
        assertEquals("incident-analysis", context.hiddenContext().get(AgentToolContextKeys.REPORT_FEATURE));
        assertEquals(
                List.of("FUNCTIONAL_ANALYSIS", "TECHNICAL_HANDOFF"),
                context.hiddenContext().get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS)
        );
        assertFalse(context.hiddenContext().containsKey(AgentToolContextKeys.GITLAB_BRANCH));
        assertFalse(context.hiddenContext().containsKey(AgentToolContextKeys.GITLAB_GROUP));
        assertEquals(context.analysisRunId(), context.hiddenContext().get(AgentToolContextKeys.ANALYSIS_RUN_ID));
        assertEquals(context.copilotSessionId(), context.hiddenContext().get(AgentToolContextKeys.COPILOT_SESSION_ID));
    }

    @Test
    void shouldCreateFollowUpSessionContextWithChatPrefix() {
        var request = new AnalysisAiChatRequest(
                "corr-chat",
                "prod",
                "main",
                "CRM/runtime",
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
        assertFalse(context.hiddenContext().containsKey(AgentToolContextKeys.REPORT_ID));
        assertFalse(context.hiddenContext().containsKey(AgentToolContextKeys.REPORT_FEATURE));
        assertFalse(context.hiddenContext().containsKey(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS));
        assertFalse(context.hiddenContext().containsKey(AgentToolContextKeys.GITLAB_BRANCH));
        assertFalse(context.hiddenContext().containsKey(AgentToolContextKeys.GITLAB_GROUP));
    }
}
