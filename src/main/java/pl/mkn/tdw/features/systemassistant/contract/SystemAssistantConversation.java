package pl.mkn.tdw.features.systemassistant.contract;

import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;

import java.util.List;

public record SystemAssistantConversation(String id, boolean busy, PageContext pageContext,
                                          List<AnalysisChatMessageResponse> messages) {
    public record PageContext(String origin, String pathname, String title, String capturedAt) {
    }
}
