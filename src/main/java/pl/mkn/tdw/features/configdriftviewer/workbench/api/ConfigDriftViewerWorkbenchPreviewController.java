package pl.mkn.tdw.features.configdriftviewer.workbench.api;

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
import pl.mkn.tdw.features.configdriftviewer.workbench
        .ConfigDriftViewerWorkbenchPreviewService;

@RestController
@RequestMapping("/api/config-drift-viewer/v1/workbench")
@RequiredArgsConstructor
@Validated
public class ConfigDriftViewerWorkbenchPreviewController {

    private final ConfigDriftViewerWorkbenchPreviewService previewService;

    @PostMapping("/preview")
    public ConfigDriftViewerWorkbenchPreviewResponse preview(
            @Valid @RequestBody ConfigDriftViewerWorkbenchPreviewRequest request
    ) {
        return previewService.preview(request);
    }

    @GetMapping("/preview/{previewId}/source")
    public ConfigDriftViewerWorkbenchSourceResponse source(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId
    ) {
        return previewService.source(previewId);
    }

    @GetMapping("/preview/{previewId}/configuration-diff")
    public ConfigDriftViewerWorkbenchConfigurationDiffResponse configurationDiff(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId
    ) {
        return previewService.configurationDiff(previewId);
    }

    @GetMapping("/preview/{previewId}/mapping")
    public ConfigDriftViewerWorkbenchMappingPage mapping(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "true") boolean changedOnly
    ) {
        return previewService.mapping(previewId, offset, limit, changedOnly);
    }

    @GetMapping("/preview/{previewId}/anonymization")
    public ConfigDriftViewerWorkbenchAnonymizationPage anonymization(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit
    ) {
        return previewService.anonymization(previewId, offset, limit);
    }

    @GetMapping("/preview/{previewId}/deep")
    public ConfigDriftViewerWorkbenchDeepResponse deep(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId
    ) {
        return previewService.deep(previewId);
    }

    @GetMapping("/preview/{previewId}/ai-input")
    public ConfigDriftViewerWorkbenchAiInputResponse aiInput(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId
    ) {
        return previewService.aiInput(previewId);
    }

    @GetMapping("/preview/{previewId}/artifact")
    public ConfigDriftViewerWorkbenchArtifactResponse artifact(
            @PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String previewId,
            @RequestParam
            @Size(min = 1, max = 256)
            @Pattern(regexp = "^(?!.*\\.\\.)(?!.*//)[A-Za-z0-9][A-Za-z0-9._/-]*$")
            String name
    ) {
        return previewService.artifact(previewId, name);
    }
}
