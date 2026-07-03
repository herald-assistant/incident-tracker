package pl.mkn.tdw.features.flowexplorer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.flowexplorer.endpoint.FlowExplorerEndpointInventoryService;

@RestController
@RequestMapping("/api/flow-explorer")
@RequiredArgsConstructor
public class FlowExplorerEndpointInventoryController {

    private final FlowExplorerEndpointInventoryService flowExplorerEndpointInventoryService;

    @GetMapping("/systems/{systemId}/endpoints")
    public FlowExplorerEndpointInventoryResponse endpoints(
            @PathVariable String systemId,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String endpointPathPrefix,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return flowExplorerEndpointInventoryService.endpoints(
                systemId,
                branch,
                endpointPathPrefix,
                httpMethod,
                refresh
        );
    }
}
