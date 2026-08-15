package pl.mkn.tdw.features.uiexplorer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui-explorer")
@RequiredArgsConstructor
public class UiExplorerInputOptionsController {

    private final UiExplorerInputOptionsService inputOptionsService;

    @GetMapping("/input-options")
    public UiExplorerInputOptionsResponse inputOptions() {
        return inputOptionsService.inputOptions();
    }
}

