package pl.mkn.tdw.features.uxinspector.ai.chat;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.report.AnalysisReportManualEditGuidance;

@Service
public class UxInspectorFollowUpPromptService {

    public String prepare(UxInspectorFollowUpChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("UX Inspector follow-up message is required.");
        }
        return request.message().trim() + AnalysisReportManualEditGuidance.forFollowUp(request.report());
    }
}
