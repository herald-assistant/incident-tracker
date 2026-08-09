package pl.mkn.tdw.features.configdriftviewer.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.configdriftviewer.job.importing
        .ConfigDriftViewerImportService;

@RestController
@RequestMapping("/api/config-drift-viewer/v1/imports")
@RequiredArgsConstructor
public class ConfigDriftViewerImportController {

    private final ConfigDriftViewerImportService importService;

    @PostMapping
    public ConfigDriftViewerJobStateSnapshot importReadOnly(@RequestBody JsonNode document) {
        return importService.importReadOnly(document);
    }
}
