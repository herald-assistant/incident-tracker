package pl.mkn.tdw.features.uiexplorer.api;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailability;

import java.util.List;

public record UiExplorerInputOptionsResponse(
        String featureId,
        UiExplorerOutputAvailability executionAvailability,
        List<SystemOption> systems,
        List<UiExplorerSectionModeAssignment> defaultSectionModes,
        List<SectionOption> sections,
        List<ModeOption> modes,
        List<ConfigurationFinding> configurationFindings
) {

    public UiExplorerInputOptionsResponse {
        systems = systems != null ? List.copyOf(systems) : List.of();
        defaultSectionModes = defaultSectionModes != null ? List.copyOf(defaultSectionModes) : List.of();
        sections = sections != null ? List.copyOf(sections) : List.of();
        modes = modes != null ? List.copyOf(modes) : List.of();
        configurationFindings = configurationFindings != null
                ? List.copyOf(configurationFindings)
                : List.of();
    }

    public record SystemOption(
            String systemId,
            String label,
            String summary
    ) {
    }

    public record SectionOption(
            UiExplorerSectionId sectionId,
            String label,
            String description
    ) {
    }

    public record ModeOption(
            UiExplorerSectionMode mode,
            String label,
            String description
    ) {
    }

    public record ConfigurationFinding(
            String severity,
            String code,
            String message,
            String entityType,
            String entityId
    ) {
    }
}
