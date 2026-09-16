package pl.mkn.tdw.frontendcatalog;

import java.util.List;
import java.util.Optional;

public record FrontendApplicationCatalog(
        String contentDigest,
        List<FrontendApplicationRegistration> frontends,
        List<FrontendConfigurationFinding> configurationFindings
) {
    public FrontendApplicationCatalog {
        frontends = frontends != null ? List.copyOf(frontends) : List.of();
        configurationFindings = configurationFindings != null ? List.copyOf(configurationFindings) : List.of();
    }

    public Optional<FrontendApplicationRegistration> findFrontend(String systemId) {
        return frontends.stream().filter(frontend -> frontend.systemId().equals(systemId)).findFirst();
    }
}

