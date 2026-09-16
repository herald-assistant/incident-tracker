package pl.mkn.tdw.features.uxinspector.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.mkn.tdw.features.uxinspector.job.importing.UxInspectorImportService;

@RestController
@RequestMapping("/api/ux-inspector/imports")
@RequiredArgsConstructor
public class UxInspectorImportController {
    private final UxInspectorImportService service;
    @PostMapping
    public UxInspectorJobStateSnapshot importReadOnly(@RequestBody JsonNode document) { return service.importReadOnly(document); }
}
