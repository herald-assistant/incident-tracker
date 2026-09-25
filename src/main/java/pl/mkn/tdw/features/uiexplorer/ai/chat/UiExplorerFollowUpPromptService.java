package pl.mkn.tdw.features.uiexplorer.ai.chat;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.report.AnalysisReportManualEditGuidance;

@Service
public class UiExplorerFollowUpPromptService {

    public String prepare(UiExplorerFollowUpChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("UI Explorer follow-up message is required.");
        }
        return "Uzyj skilla `ui-explorer-follow-up-chat` przed odpowiedzia.\n\n"
                + request.message().trim()
                + AnalysisReportManualEditGuidance.forFollowUp(request.report());
    }
}
