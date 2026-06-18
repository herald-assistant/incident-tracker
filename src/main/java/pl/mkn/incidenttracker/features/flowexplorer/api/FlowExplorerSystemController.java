package pl.mkn.incidenttracker.features.flowexplorer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSystemSelectionService;

import java.util.List;

@RestController
@RequestMapping("/flow-explorer")
@RequiredArgsConstructor
public class FlowExplorerSystemController {

    private final FlowExplorerSystemSelectionService flowExplorerSystemSelectionService;

    @GetMapping("/systems")
    public List<FlowExplorerSystemOptionResponse> systems() {
        return flowExplorerSystemSelectionService.systems();
    }
}
