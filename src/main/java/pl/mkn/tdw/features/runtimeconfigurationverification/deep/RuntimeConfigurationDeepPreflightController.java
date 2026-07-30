package pl.mkn.tdw.features.runtimeconfigurationverification.deep;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflight;

@RestController
@RequestMapping("/api/runtime-configuration-verification/deep-preflight")
@RequiredArgsConstructor
public class RuntimeConfigurationDeepPreflightController {

    private final RuntimeConfigurationDeepPreflightService preflightService;

    @GetMapping
    public RuntimeConfigurationDeepPreflight check(
            @RequestParam String repositoryId,
            @RequestParam String systemId,
            @RequestParam(required = false) String codeRef
    ) {
        return preflightService.check(repositoryId, systemId, codeRef);
    }
}
