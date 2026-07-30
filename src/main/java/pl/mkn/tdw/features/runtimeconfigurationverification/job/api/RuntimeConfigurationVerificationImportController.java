package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.importing
        .RuntimeConfigurationVerificationImportService;

@RestController
@RequestMapping("/api/runtime-configuration-verification/imports")
@RequiredArgsConstructor
public class RuntimeConfigurationVerificationImportController {

    private final RuntimeConfigurationVerificationImportService importService;

    @PostMapping
    public RuntimeConfigurationVerificationJobStateSnapshot importReadOnly(@RequestBody JsonNode document) {
        return importService.importReadOnly(document);
    }
}
