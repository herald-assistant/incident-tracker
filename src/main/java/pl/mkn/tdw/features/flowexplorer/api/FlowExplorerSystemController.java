package pl.mkn.tdw.features.flowexplorer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSystemSelectionService;

import java.util.List;

@RestController
@RequestMapping("/api/flow-explorer")
@RequiredArgsConstructor
public class FlowExplorerSystemController {

    private final FlowExplorerSystemSelectionService flowExplorerSystemSelectionService;

    @GetMapping("/systems")
    public List<FlowExplorerSystemOptionResponse> systems() {
        return flowExplorerSystemSelectionService.systems();
    }
}
