package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteGraph;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteGraphDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceResponse;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceResponse;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceService;

@RestController
@RequestMapping("/api/gitlab/frontend")
@RequiredArgsConstructor
public class GitLabFrontendDiscoveryController {

    private final GitLabFrontendRouteGraphDiscoveryService routeGraphDiscoveryService;
    private final GitLabFrontendScreenReachabilityService screenReachabilityService;
    private final GitLabAngularRouteBranchSliceService routeBranchSliceService;
    private final GitLabTypeScriptSymbolSliceService typeScriptSymbolSliceService;

    @PostMapping("/catalog")
    public GitLabFrontendRouteGraph discoverCatalog(
            @Valid @RequestBody GitLabFrontendCatalogApiRequest request
    ) {
        return routeGraphDiscoveryService.discover(request.toScope(), request.limits());
    }

    @PostMapping("/screen-reachability")
    public GitLabFrontendScreenReachabilityGraph buildScreenReachability(
            @Valid @RequestBody GitLabFrontendScreenReachabilityApiRequest request
    ) {
        return screenReachabilityService.build(request.toIntegrationRequest());
    }

    @PostMapping("/route-branch-slice")
    public GitLabAngularRouteBranchSliceResponse readRouteBranchSlice(
            @Valid @RequestBody GitLabAngularRouteBranchSliceApiRequest request
    ) {
        return routeBranchSliceService.readBranchSlice(request.toIntegrationRequest());
    }

    @PostMapping("/typescript-symbol-slice")
    public GitLabTypeScriptSymbolSliceResponse readTypeScriptSymbolSlice(
            @Valid @RequestBody GitLabTypeScriptSymbolSliceApiRequest request
    ) {
        return typeScriptSymbolSliceService.readSymbolSlice(request.toIntegrationRequest());
    }
}
