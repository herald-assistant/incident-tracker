package pl.mkn.tdw.features.uiexplorer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerScreenCatalog;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerScreenCatalogService;

@RestController
@RequestMapping("/api/ui-explorer")
@RequiredArgsConstructor
public class UiExplorerScreenCatalogController {

    private final UiExplorerScreenCatalogService screenCatalogService;

    @GetMapping("/screens")
    public UiExplorerScreenCatalog screens(
            @RequestParam String systemId,
            @RequestParam String branch,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return screenCatalogService.loadCatalog(systemId, branch, refresh);
    }
}
