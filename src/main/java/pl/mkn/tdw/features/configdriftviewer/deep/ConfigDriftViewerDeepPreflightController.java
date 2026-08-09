package pl.mkn.tdw.features.configdriftviewer.deep;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepPreflight;

@RestController
@RequestMapping("/api/config-drift-viewer/v1/deep-preflight")
@RequiredArgsConstructor
public class ConfigDriftViewerDeepPreflightController {

    private final ConfigDriftViewerDeepPreflightService preflightService;

    @GetMapping
    public ConfigDriftViewerDeepPreflight check(
            @RequestParam String repositoryId,
            @RequestParam String systemId,
            @RequestParam(required = false) String codeRef
    ) {
        return preflightService.check(repositoryId, systemId, codeRef);
    }
}
