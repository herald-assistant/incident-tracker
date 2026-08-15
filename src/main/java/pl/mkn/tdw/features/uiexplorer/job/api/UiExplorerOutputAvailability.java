package pl.mkn.tdw.features.uiexplorer.job.api;

import java.util.List;

public record UiExplorerOutputAvailability(
        UiExplorerOutputAvailabilityStatus status,
        String code,
        String message,
        List<String> missingCapabilities
) {

    public UiExplorerOutputAvailability {
        missingCapabilities = missingCapabilities != null ? List.copyOf(missingCapabilities) : List.of();
    }
}

