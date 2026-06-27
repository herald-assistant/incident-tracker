package pl.mkn.tdw.features.flowexplorer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.flowexplorer.FlowExplorerProperties;

@RestController
@RequestMapping("/api/flow-explorer")
@RequiredArgsConstructor
public class FlowExplorerConfigController {

    private final FlowExplorerProperties flowExplorerProperties;

    @GetMapping("/config")
    public FlowExplorerConfigResponse config() {
        return new FlowExplorerConfigResponse(flowExplorerProperties.getDefaultBranch());
    }
}
