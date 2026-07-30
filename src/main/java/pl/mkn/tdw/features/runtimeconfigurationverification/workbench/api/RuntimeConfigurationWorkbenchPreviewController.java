package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench
        .RuntimeConfigurationWorkbenchPreviewService;

@RestController
@RequestMapping("/api/runtime-configuration-verification/workbench")
@RequiredArgsConstructor
public class RuntimeConfigurationWorkbenchPreviewController {

    private final RuntimeConfigurationWorkbenchPreviewService previewService;

    @PostMapping("/preview")
    public RuntimeConfigurationWorkbenchPreviewResponse preview(
            @Valid @RequestBody RuntimeConfigurationWorkbenchPreviewRequest request
    ) {
        return previewService.preview(request);
    }
}
