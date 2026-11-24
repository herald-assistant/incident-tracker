package pl.mkn.tdw.features.systemassistant.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemAssistantMessageRequest(@NotBlank String message,
                                             @NotBlank @Size(max = 100) String profileId,
                                             @Size(max = 120) String model,
                                             @Size(max = 30) String reasoningEffort) {}
