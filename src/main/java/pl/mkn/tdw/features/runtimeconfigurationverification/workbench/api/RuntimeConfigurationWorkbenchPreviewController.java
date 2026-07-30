package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench
        .RuntimeConfigurationWorkbenchPreviewService;

@RestController
@RequestMapping("/api/runtime-configuration-verification/workbench")
@RequiredArgsConstructor
@Validated
public class RuntimeConfigurationWorkbenchPreviewController {

    private final RuntimeConfigurationWorkbenchPreviewService previewService;

    @PostMapping("/preview")
    public RuntimeConfigurationWorkbenchPreviewResponse preview(
            @Valid @RequestBody RuntimeConfigurationWorkbenchPreviewRequest request
    ) {
        return previewService.preview(request);
    }

    @GetMapping("/preview/{previewId}/source")
    public RuntimeConfigurationWorkbenchSourceResponse source(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId
    ) {
        return previewService.source(previewId);
    }

    @GetMapping("/preview/{previewId}/mapping")
    public RuntimeConfigurationWorkbenchMappingPage mapping(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "true") boolean changedOnly
    ) {
        return previewService.mapping(previewId, offset, limit, changedOnly);
    }

    @GetMapping("/preview/{previewId}/anonymization")
    public RuntimeConfigurationWorkbenchAnonymizationPage anonymization(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit
    ) {
        return previewService.anonymization(previewId, offset, limit);
    }

    @GetMapping("/preview/{previewId}/deep")
    public RuntimeConfigurationWorkbenchDeepResponse deep(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId
    ) {
        return previewService.deep(previewId);
    }

    @GetMapping("/preview/{previewId}/ai-input")
    public RuntimeConfigurationWorkbenchAiInputResponse aiInput(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId
    ) {
        return previewService.aiInput(previewId);
    }

    @GetMapping("/preview/{previewId}/artifact")
    public RuntimeConfigurationWorkbenchArtifactResponse artifact(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId,
            @RequestParam
            @Size(min = 1, max = 256)
            @Pattern(regexp = "^(?!.*\\.\\.)(?!.*//)[A-Za-z0-9][A-Za-z0-9._/-]*$")
            String name
    ) {
        return previewService.artifact(previewId, name);
    }
}
