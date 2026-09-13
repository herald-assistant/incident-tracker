package pl.mkn.tdw.features.operationalcontextassistance.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceOptionsService;

@RestController
@RequestMapping("/api/operational-context/assistance/source-options")
@RequiredArgsConstructor
public class OperationalContextAssistanceSourceOptionsController {

    private final OperationalContextGitLabSourceOptionsService sourceOptionsService;

    @GetMapping
    public OperationalContextAssistanceSourceOptions get() {
        return sourceOptionsService.getOptions();
    }
}
