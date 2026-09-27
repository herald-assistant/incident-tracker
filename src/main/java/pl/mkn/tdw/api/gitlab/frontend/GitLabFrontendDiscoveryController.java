package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendRouteGraph;
import pl.mkn.tdw.integrations.gitlab.GitLabFrontendRouteGraphPort;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendScreenReachabilityGraph;
import pl.mkn.tdw.integrations.gitlab.GitLabFrontendScreenReachabilityPort;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabAngularRouteBranchSliceResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabAngularRouteBranchSlicePort;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabTypeScriptSymbolSliceResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabTypeScriptSymbolSlicePort;

@RestController
@RequestMapping("/api/gitlab/frontend")
@RequiredArgsConstructor
public class GitLabFrontendDiscoveryController {

    private final GitLabFrontendRouteGraphPort routeGraphDiscoveryService;
    private final GitLabFrontendScreenReachabilityPort screenReachabilityService;
    private final GitLabAngularRouteBranchSlicePort routeBranchSliceService;
    private final GitLabTypeScriptSymbolSlicePort typeScriptSymbolSliceService;

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
