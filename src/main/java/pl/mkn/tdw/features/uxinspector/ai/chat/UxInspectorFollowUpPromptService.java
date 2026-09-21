package pl.mkn.tdw.features.uxinspector.ai.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UxInspectorFollowUpPromptService {
    private final ObjectMapper objectMapper;

    public String prepare(UxInspectorFollowUpChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message()) || request.report() == null
                || request.context() == null || request.context().view() == null
                || request.context().sourceRevision() == null) {
            throw new IllegalArgumentException("Complete UX Inspector follow-up context is required.");
        }
        return """
                # UX Inspector follow-up chat

                Odpowiedz analitykowi funkcjonalnie i prostym jezykiem. Kontynuuj styl raportu.
                Raport ponizej jest kontekstem tylko do odczytu i nie moze byc zmieniany.

                ## Wskazany element i zamrozona rewizja
                - systemId: %s
                - viewId: %s
                - routePattern: %s
                - sourceRevision: %s
                - target: %s

                ## Biezacy raport read-only
                %s

                ## Pytanie uzytkownika
                %s
                """.formatted(
                request.context().systemId(),
                request.context().view().viewId(),
                request.context().view().routePattern(),
                request.context().sourceRevision().revision(),
                targetLabel(request),
                reportJson(request),
                request.message().trim()
        ).trim();
    }

    private String targetLabel(UxInspectorFollowUpChatRequest request) {
        var capture = request.initialRequest().capture();
        if (capture == null || capture.target() == null) return "wskazany element";
        if (StringUtils.hasText(capture.target().accessibleName())) return capture.target().accessibleName().trim();
        if (StringUtils.hasText(capture.target().text())) return capture.target().text().trim();
        return capture.target().tag();
    }

    private String reportJson(UxInspectorFollowUpChatRequest request) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request.report());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("UX Inspector report cannot be rendered for follow-up.", exception);
        }
    }
}
