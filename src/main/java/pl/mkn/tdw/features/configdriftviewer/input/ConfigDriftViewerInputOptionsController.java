package pl.mkn.tdw.features.configdriftviewer.input;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config-drift-viewer/v1/input-options")
@RequiredArgsConstructor
public class ConfigDriftViewerInputOptionsController {

    private final ConfigDriftViewerInputOptionsService inputOptionsService;

    @GetMapping
    public ConfigDriftViewerInputOptions getOptions() {
        return inputOptionsService.getOptions();
    }
}
