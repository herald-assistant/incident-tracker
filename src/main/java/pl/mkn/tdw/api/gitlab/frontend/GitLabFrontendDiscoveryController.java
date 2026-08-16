package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteGraph;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteGraphDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenGraphContext;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenGraphContextService;

@RestController
@RequestMapping("/api/gitlab/frontend")
@RequiredArgsConstructor
public class GitLabFrontendDiscoveryController {

    private final GitLabFrontendRouteGraphDiscoveryService routeGraphDiscoveryService;
    private final GitLabFrontendScreenGraphContextService screenGraphContextService;

    @PostMapping("/catalog")
    public GitLabFrontendRouteGraph discoverCatalog(
            @Valid @RequestBody GitLabFrontendCatalogApiRequest request
    ) {
        return routeGraphDiscoveryService.discover(request.toScope(), request.limits());
    }

    @PostMapping("/screen-context")
    public GitLabFrontendScreenGraphContext buildScreenContext(
            @Valid @RequestBody GitLabFrontendScreenContextApiRequest request
    ) {
        return screenGraphContextService.build(request.toIntegrationRequest());
    }
}
