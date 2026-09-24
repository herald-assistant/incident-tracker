package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

@Component
@RequiredArgsConstructor
public class CopilotIncidentFollowUpRunAssembler {

    private static final String FOLLOW_UP_REPORT_GUIDANCE = """
            W follow-up odpowiadaj na biezace pytanie operatora. Zmieniaj zapisany raport tylko wtedy,
            gdy najnowsza wiadomosc operatora jawnie prosi o jego aktualizacje; sama prosba o wyjasnienie,
            dodatkowy dowod albo zmiane odpowiedzi w rozmowie nie upowaznia do mutacji raportu.
            Przed edycja odczytaj potrzebna sekcje przez report_get_current(sectionId). Dla malej,
            jednoznacznej korekty uzyj report_patch_section z digestem i dokladnym starym fragmentem;
            dla przebudowy sekcji report_upsert_section, a dla naglowka lub globalnych metadata
            report_update_header albo report_update_meta. Zachowaj niezmieniane pola i sekcje.
            Po zapisie sprawdz manifest przez report_get_current i opisz operatorowi, co zmieniono.
            Gdy zapis jest odrzucony lub brak raportu, nie twierdz, ze dokument zostal zmieniony.
            """;

    private static final CopilotToolDescriptionContext TOOL_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotIncidentToolSessionContextFactory toolSessionContextFactory;
    private final CopilotIncidentSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotIncidentToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotIncidentRunRequestFactory runRequestFactory;

    public CopilotRunRequest assemble(AnalysisAiChatRequest request) {
        if (request == null || !StringUtils.hasText(request.copilotSessionId())) {
            throw new IllegalArgumentException("Copilot follow-up requires copilotSessionId for session resume.");
        }

        var toolSessionContext = toolSessionContextFactory.fromChatRequest(request);
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext, TOOL_DESCRIPTION_CONTEXT);
        var toolAccessPolicy = toolAccessPolicyFactory.createForFollowUp(request, registeredTools);
        var sessionConfigRequest = sessionConfigRequestFactory.create(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy,
                request.options()
        ).withDurableSystemInstructions(FOLLOW_UP_REPORT_GUIDANCE);
        var prompt = request.message() != null ? request.message().trim() : "";

        return runRequestFactory.create(
                request.correlationId(),
                request.authRef(),
                CopilotSessionTarget.existing(request.copilotSessionId()),
                prompt,
                sessionConfigRequest,
                java.util.List.of()
        ).withInitialReport(request.report());
    }
}
