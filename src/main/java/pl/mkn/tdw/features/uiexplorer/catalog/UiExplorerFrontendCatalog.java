package pl.mkn.tdw.features.uiexplorer.catalog;

import java.util.List;
import java.util.Optional;

public record UiExplorerFrontendCatalog(
        String contentDigest,
        List<UiExplorerFrontendRegistration> frontends,
        List<UiExplorerConfigurationFinding> configurationFindings
) {

    public UiExplorerFrontendCatalog {
        frontends = frontends != null ? List.copyOf(frontends) : List.of();
        configurationFindings = configurationFindings != null
                ? List.copyOf(configurationFindings)
                : List.of();
    }

    public Optional<UiExplorerFrontendRegistration> findFrontend(String systemId) {
        return frontends.stream()
                .filter(frontend -> frontend.systemId().equals(systemId))
                .findFirst();
    }
}

