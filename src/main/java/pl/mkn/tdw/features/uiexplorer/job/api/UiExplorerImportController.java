package pl.mkn.tdw.features.uiexplorer.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.uiexplorer.job.importing.UiExplorerImportService;

@RestController
@RequestMapping("/api/ui-explorer/imports")
@RequiredArgsConstructor
public class UiExplorerImportController {

    private final UiExplorerImportService importService;

    @PostMapping
    public UiExplorerJobStateSnapshot importReadOnly(@RequestBody JsonNode document) {
        return importService.importReadOnly(document);
    }
}
