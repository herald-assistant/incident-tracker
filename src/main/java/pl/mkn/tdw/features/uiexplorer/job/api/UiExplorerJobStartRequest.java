package pl.mkn.tdw.features.uiexplorer.job.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record UiExplorerJobStartRequest(
        @NotBlank(message = "systemId must not be blank")
        @Size(max = 120, message = "systemId must not exceed 120 characters")
        String systemId,
        @NotBlank(message = "branch must not be blank")
        @Size(max = 160, message = "branch must not exceed 160 characters")
        String branch,
        @NotBlank(message = "screenId must not be blank")
        @Size(max = 240, message = "screenId must not exceed 240 characters")
        String screenId,
        @NotBlank(message = "sourceRevision must not be blank")
        @Size(max = 160, message = "sourceRevision must not exceed 160 characters")
        String sourceRevision,
        @Size(max = 8, message = "sectionModes must contain at most 8 entries")
        Map<UiExplorerSectionId, UiExplorerSectionMode> sectionModes,
        @Size(max = 4000, message = "scenarioDescription must not exceed 4000 characters")
        String scenarioDescription,
        @Size(max = 80, message = "model must not exceed 80 characters")
        String model,
        @Size(max = 40, message = "reasoningEffort must not exceed 40 characters")
        String reasoningEffort
) {

    public UiExplorerJobStartRequest {
        systemId = normalize(systemId);
        branch = normalize(branch);
        screenId = normalize(screenId);
        sourceRevision = normalize(sourceRevision);
        sectionModes = sectionModes != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(sectionModes))
                : Map.of();
        scenarioDescription = normalize(scenarioDescription);
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    @AssertTrue(message = "sectionModes must contain only known sections with a mode")
    public boolean isSectionModesValid() {
        return sectionModes.entrySet().stream()
                .allMatch(entry -> entry.getKey() != null && entry.getValue() != null);
    }

    @AssertTrue(message = "at least one section must be COMPACT or DEEP")
    public boolean isAtLeastOneSectionActive() {
        return sectionModes.values().stream().anyMatch(mode -> mode != null && mode != UiExplorerSectionMode.OFF);
    }

    public List<UiExplorerSectionModeAssignment> resolvedSectionModes() {
        return java.util.Arrays.stream(UiExplorerSectionId.values())
                .map(sectionId -> new UiExplorerSectionModeAssignment(
                        sectionId,
                        sectionModes.getOrDefault(sectionId, UiExplorerSectionMode.OFF)
                ))
                .toList();
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown UI Explorer request field: " + fieldName);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
