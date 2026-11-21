package pl.mkn.tdw.features.systemassistant.ai;

import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService.Snapshot;
import pl.mkn.tdw.features.systemassistant.contract.*;
import pl.mkn.tdw.shared.ai.*;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import java.util.List;
import java.util.function.Consumer;

public interface SystemAssistantAiPort {
    void requireReady();
    Reply answer(String turnId, Snapshot configuration, SystemAssistantConversation.PageContext page,
                 List<AnalysisChatMessageResponse> history, SystemAssistantMessageRequest request,
                 Consumer<AnalysisEvidenceSection> evidence, Consumer<AnalysisAiActivityEvent> activity);
    record Reply(String content, AnalysisAiUsage usage) {}
}
