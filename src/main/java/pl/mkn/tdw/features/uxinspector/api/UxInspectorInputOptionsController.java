package pl.mkn.tdw.features.uxinspector.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ux-inspector")
@RequiredArgsConstructor
public class UxInspectorInputOptionsController {
    private final UxInspectorInputOptionsService service;

    @GetMapping("/input-options")
    public UxInspectorInputOptionsResponse inputOptions() {
        return service.inputOptions();
    }

    @GetMapping("/views")
    public UxInspectorViewCatalogResponse views(@RequestParam String systemId, @RequestParam String branch) {
        return service.views(systemId, branch);
    }
}
