package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CopilotIncidentToolSessionContextFactory {

    private final CopilotIncidentHiddenToolContextFactory hiddenToolContextFactory;

    public CopilotToolSessionContext fromInitialRequest(InitialAnalysisRequest request) {
        return create("analysis-", hiddenToolContextFactory.fromInitialRequest(request));
    }

    public CopilotToolSessionContext fromChatRequest(AnalysisAiChatRequest request) {
        if (request != null && StringUtils.hasText(request.copilotSessionId())) {
            return createWithSessionId(
                    request.copilotSessionId(),
                    hiddenToolContextFactory.fromChatRequest(request)
            );
        }

        return create("analysis-chat-", hiddenToolContextFactory.fromChatRequest(request));
    }

    private CopilotToolSessionContext create(String sessionIdPrefix, Map<String, Object> hiddenContext) {
        var analysisRunId = UUID.randomUUID().toString();
        var copilotSessionId = sessionIdPrefix + analysisRunId;

        return new CopilotToolSessionContext(
                analysisRunId,
                copilotSessionId,
                hiddenContext
        );
    }

    private CopilotToolSessionContext createWithSessionId(String copilotSessionId, Map<String, Object> hiddenContext) {
        return new CopilotToolSessionContext(
                UUID.randomUUID().toString(),
                copilotSessionId.trim(),
                hiddenContext
        );
    }
}
