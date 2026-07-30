package pl.mkn.tdw.features.runtimeconfigurationverification.input;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime-configuration-verification/input-options")
@RequiredArgsConstructor
public class RuntimeConfigurationVerificationInputOptionsController {

    private final RuntimeConfigurationVerificationInputOptionsService inputOptionsService;

    @GetMapping
    public RuntimeConfigurationVerificationInputOptions getOptions() {
        return inputOptionsService.getOptions();
    }
}
