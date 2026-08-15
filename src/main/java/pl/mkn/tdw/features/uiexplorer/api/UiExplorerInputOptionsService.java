package pl.mkn.tdw.features.uiexplorer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerFrontendCatalogService;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerProfile;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailability;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailabilityStatus;

import java.util.Arrays;
import java.util.List;

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
                Arrays.stream(UiExplorerProfile.values()).map(profile ->
                        new UiExplorerInputOptionsResponse.ProfileOption(
                                profile,
                                profile.label(),
                                profile.description(),
                                profile.defaultSectionModes().entrySet().stream()
                                        .map(entry -> new UiExplorerSectionModeAssignment(entry.getKey(), entry.getValue()))
                                        .toList()
                        )).toList(),
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
}
