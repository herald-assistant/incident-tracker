package pl.mkn.tdw.features.systemassistant.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemAssistantMessageRequest(@NotBlank @Size(max = 12_000) String message,
                                             @Size(max = 120) String model,
                                             @Size(max = 30) String reasoningEffort) {}
