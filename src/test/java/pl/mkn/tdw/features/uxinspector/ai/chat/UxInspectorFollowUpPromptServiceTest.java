package pl.mkn.tdw.features.uxinspector.ai.chat;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorFollowUpPromptServiceTest {
    @Test
    void shouldSendOnlyNewMessageToExistingSession() {
        var request = new UxInspectorFollowUpChatRequest("crm-follow-up",
                new pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest(
                        "crm-agent-portal", "main", VIEW_ID, REVISION, "Dlaczego zapis jest zablokowany?",
                        capture(), "gpt-crm", "medium"), targetContext(),
                "  Co jeszcze wpływa na zapis?  ", "ux-inspector-crm", AnalysisAiAuthRef.localToken(null));

        var prompt = new UxInspectorFollowUpPromptService().prepare(request);

        assertThat(prompt).isEqualTo("Co jeszcze wpływa na zapis?");
    }
}
