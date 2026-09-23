package pl.mkn.tdw.features.uiexplorer.ai.chat;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerFollowUpPromptServiceTest {

    @Test
    void shouldSendOnlySkillCueAndNewMessage() {
        var chatRequest = new UiExplorerFollowUpChatRequest(
                "crm-ui-run-1", request(), context(), "  Co dzieje sie po zapisie?  ",
                "crm-ui-session-1", AnalysisAiAuthRef.localToken(null)
        );

        var prompt = new UiExplorerFollowUpPromptService().prepare(chatRequest);

        assertThat(prompt).isEqualTo("Uzyj skilla `ui-explorer-follow-up-chat` przed odpowiedzia.\n\n"
                + "Co dzieje sie po zapisie?");
    }
}
