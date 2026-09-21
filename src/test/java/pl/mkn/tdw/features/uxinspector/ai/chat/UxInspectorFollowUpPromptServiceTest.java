package pl.mkn.tdw.features.uxinspector.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorFollowUpPromptServiceTest {
    @Test
    void shouldKeepReportReadOnlyAndAddressANonTechnicalAnalyst() {
        var report = new AnalysisReport("ux-inspector-report-crm", "UX Inspector", "CRM", "Zapis kontaktu",
                List.of(new AnalysisReportSection("answer", "Odpowiedź", 1,
                        "Pole jest wymagane przed zapisem.", AnalysisReportMeta.empty())), AnalysisReportMeta.empty());
        var request = new UxInspectorFollowUpChatRequest("crm-follow-up",
                new pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest(
                        "crm-agent-portal", "main", VIEW_ID, REVISION, "Dlaczego zapis jest zablokowany?",
                        capture(), "gpt-crm", "medium"), targetContext(), report,
                "Co jeszcze wpływa na zapis?", "ux-inspector-crm", AnalysisAiAuthRef.localToken(null));

        var prompt = new UxInspectorFollowUpPromptService(new ObjectMapper()).prepare(request);

        assertThat(prompt).contains("funkcjonalnie i prostym jezykiem", "kontekstem tylko do odczytu",
                "Co jeszcze wpływa na zapis?", REVISION, "Zapisz kontakt");
        assertThat(prompt).doesNotContain("report_upsert_section", "report_update_header");
    }
}
