package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.COMPLETED_AT;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.snapshot;

class UiExplorerReportChangeSanitizerTest {
    @Test
    void preservesOnlyRawBeforeAndAfterForSavedChatChange() {
        var base = snapshot(UiExplorerJobStatus.COMPLETED);
        var change = new AnalysisEvidenceSection("report", "report-change", List.of(
                new AnalysisEvidenceItem("Dane klienta", List.of(
                        new AnalysisEvidenceAttribute("before", "**Stary opis CRM**"),
                        new AnalysisEvidenceAttribute("after", "**Nowy opis CRM**"),
                        new AnalysisEvidenceAttribute("hiddenScope", "CRM_INTERNAL_SECRET")
                ))));
        var message = new AnalysisChatMessageResponse("crm-chat-1", "ASSISTANT", "COMPLETED",
                "Zaktualizowano opis.", null, null, COMPLETED_AT, COMPLETED_AT, COMPLETED_AT,
                List.of(change), List.of(), List.of(), null);
        var withChat = new UiExplorerJobStateSnapshot(base.jobId(), base.request(), base.status(),
                base.currentStepCode(), base.currentStepLabel(), base.errorCode(), base.errorMessage(),
                base.createdAt(), base.updatedAt(), base.completedAt(), base.steps(), base.contextSections(),
                base.toolEvidenceSections(), base.aiActivityEvents(), base.toolFeedback(), base.preparedPrompt(),
                base.result(), base.report(), base.usage(), base.sourceRevision(), base.outputAvailability(),
                base.exportAvailable(), List.of(message), base.chatAvailability());

        var sanitized = new UiExplorerLocalRunSnapshotSanitizer().sanitize(withChat);

        assertThat(sanitized.chatMessages()).hasSize(1);
        assertThat(sanitized.chatMessages().get(0).toolEvidenceSections()).hasSize(1);
        assertThat(sanitized.chatMessages().get(0).toolEvidenceSections().get(0).items().get(0).attributes())
                .extracting(AnalysisEvidenceAttribute::name)
                .containsExactly("before", "after");
        assertThat(sanitized.chatMessages().get(0).toolEvidenceSections().get(0).items().get(0).attributes())
                .extracting(AnalysisEvidenceAttribute::value)
                .containsExactly("**Stary opis CRM**", "**Nowy opis CRM**");
    }
}
