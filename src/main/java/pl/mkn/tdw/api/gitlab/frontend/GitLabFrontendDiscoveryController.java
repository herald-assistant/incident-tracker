package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteCatalog;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenSourceContext;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceDiscoveryService;

@RestController
@RequestMapping("/api/gitlab/frontend")
@RequiredArgsConstructor
public class GitLabFrontendDiscoveryController {

    private final GitLabFrontendSourceDiscoveryService gitLabFrontendSourceDiscoveryService;

    @PostMapping("/catalog")
    public GitLabFrontendRouteCatalog discoverCatalog(
            @Valid @RequestBody GitLabFrontendCatalogApiRequest request
    ) {
        return gitLabFrontendSourceDiscoveryService.discoverCatalog(request.toIntegrationRequest());
    }

    @PostMapping("/screen-context")
    public GitLabFrontendScreenSourceContext buildScreenContext(
            @Valid @RequestBody GitLabFrontendScreenContextApiRequest request
    ) {
        return gitLabFrontendSourceDiscoveryService.buildScreenContext(request.toIntegrationRequest());
    }
}
