package pl.mkn.tdw.features.uiexplorer.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.frontendcatalog.FrontendApplicationCatalogService;

@Service
@RequiredArgsConstructor
public class UiExplorerFrontendCatalogService {
    private final FrontendApplicationCatalogService frontendApplicationCatalogService;

    public UiExplorerFrontendCatalog loadCatalog() {
        var catalog = frontendApplicationCatalogService.loadCatalog();
        return new UiExplorerFrontendCatalog(
                catalog.contentDigest(),
                catalog.frontends().stream().map(frontend -> new UiExplorerFrontendRegistration(
                        frontend.systemId(), frontend.label(), frontend.summary(), frontend.repositoryId(),
                        frontend.projectPath(), frontend.gitLabGroup(), frontend.gitLabProjectName(),
                        frontend.defaultBranch(), frontend.searchMode(), frontend.pathPrefixes()
                )).toList(),
                catalog.configurationFindings().stream().map(finding -> new UiExplorerConfigurationFinding(
                        finding.severity(), finding.code(), finding.message(), finding.entityType(), finding.entityId()
                )).toList()
        );
    }
}
