package pl.mkn.tdw.features.systemassistant.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemAssistantMessageRequest(@NotBlank String message,
                                             @NotBlank @Size(max = 100) String profileId,
                                             @Size(max = 120) String model,
                                             @Size(max = 30) String reasoningEffort,
                                             CurrentPage pageContext) {
    public record CurrentPage(String origin, String pathname, String url, String title, String capturedAt) {
        public SystemAssistantConversation.PageContext openingContext() {
            return new SystemAssistantConversation.PageContext(origin, pathname, title, capturedAt);
        }
    }
}
