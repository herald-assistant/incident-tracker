package pl.mkn.tdw.features.uiexplorer.ai.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UiExplorerFollowUpPromptService {

    private final ObjectMapper objectMapper;

    public String prepare(UiExplorerFollowUpChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message()) || request.report() == null
                || request.context() == null || request.context().screen() == null
                || request.context().sourceRevision() == null) {
            throw new IllegalArgumentException("Complete UI Explorer follow-up context is required.");
        }
        return """
                # UI Explorer follow-up chat

                Uzyj skilla `ui-explorer-follow-up-chat` przed odpowiedzia.
                Odpowiedz analitykowi funkcjonalnie, bez zakladania znajomosci kodu.
                Ponizszy raport jest tylko kontekstem do odczytu. Nie zmieniaj go.

                ## Widok i zamrozona rewizja
                - systemId: %s
                - screenId: %s
                - routePattern: %s
                - sourceRevision: %s

                ## Biezacy raport read-only
                %s

                ## Pytanie uzytkownika
                %s
                """.formatted(
                request.context().systemId(),
                request.context().screen().screenId(),
                request.context().screen().routePattern(),
                request.context().sourceRevision().revision(),
                reportJson(request),
                request.message().trim()
        ).trim();
    }

    private String reportJson(UiExplorerFollowUpChatRequest request) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request.report());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("UI Explorer report cannot be rendered for follow-up.", exception);
        }
    }
}
