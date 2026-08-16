package pl.mkn.tdw.features.uiexplorer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerFrontendCatalogService;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailability;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailabilityStatus;

import java.util.Arrays;
import java.util.List;

import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.ACTIONS_AND_OUTCOMES;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.DATA_AND_SERVICES;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.FORMS_AND_RULES;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.NAVIGATION_AND_ACCESS;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.OVERVIEW;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.SCREEN_STRUCTURE;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.STATE_AND_SYNCHRONIZATION;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.VARIANTS_AND_FAILURES;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode.COMPACT;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode.DEEP;

@Service
@RequiredArgsConstructor
public class UiExplorerInputOptionsService {

    public static final String FEATURE_ID = "ui-explorer";

    private final UiExplorerFrontendCatalogService frontendCatalogService;

    public UiExplorerInputOptionsResponse inputOptions() {
        var catalog = frontendCatalogService.loadCatalog();
        return new UiExplorerInputOptionsResponse(
                FEATURE_ID,
                new UiExplorerOutputAvailability(
                        UiExplorerOutputAvailabilityStatus.AVAILABLE,
                        "UI_EXPLORER_ANALYSIS_AVAILABLE",
                        "Frontend selection, screen catalog, bounded source context and AI analysis are available.",
                        List.of()
                ),
                catalog.frontends().stream()
                        .map(frontend -> new UiExplorerInputOptionsResponse.SystemOption(
                                frontend.systemId(),
                                frontend.label(),
                                frontend.summary()
                        ))
                        .toList(),
                functionalDefaultSectionModes(),
                Arrays.stream(UiExplorerSectionId.values())
                        .map(section -> new UiExplorerInputOptionsResponse.SectionOption(
                                section,
                                section.label(),
                                section.description()
                        ))
                        .toList(),
                Arrays.stream(UiExplorerSectionMode.values())
                        .map(mode -> new UiExplorerInputOptionsResponse.ModeOption(
                                mode,
                                mode.label(),
                                mode.description()
                        ))
                        .toList(),
                catalog.configurationFindings().stream()
                        .map(finding -> new UiExplorerInputOptionsResponse.ConfigurationFinding(
                                finding.severity(),
                                finding.code(),
                                finding.message(),
                                finding.entityType(),
                                finding.entityId()
                        ))
                        .toList()
        );
    }

    private List<UiExplorerSectionModeAssignment> functionalDefaultSectionModes() {
        return List.of(
                new UiExplorerSectionModeAssignment(OVERVIEW, DEEP),
                new UiExplorerSectionModeAssignment(NAVIGATION_AND_ACCESS, COMPACT),
                new UiExplorerSectionModeAssignment(SCREEN_STRUCTURE, DEEP),
                new UiExplorerSectionModeAssignment(ACTIONS_AND_OUTCOMES, DEEP),
                new UiExplorerSectionModeAssignment(FORMS_AND_RULES, DEEP),
                new UiExplorerSectionModeAssignment(DATA_AND_SERVICES, COMPACT),
                new UiExplorerSectionModeAssignment(STATE_AND_SYNCHRONIZATION, COMPACT),
                new UiExplorerSectionModeAssignment(VARIANTS_AND_FAILURES, DEEP)
        );
    }
}
