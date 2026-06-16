package pl.mkn.incidenttracker.api.uiconfig;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui/config")
@RequiredArgsConstructor
public class UiConfigController {

    private final UiConfigService uiConfigService;

    @GetMapping
    public UiConfigResponse config() {
        return uiConfigService.currentConfig();
    }
}
