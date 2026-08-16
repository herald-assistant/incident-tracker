package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitLabFrontendRouteSourceTraversalServiceTest {

    @Mock
    private GitLabRepositoryPort repositoryPort;

    private GitLabFrontendRouteSourceTraversalService service;

    @BeforeEach
    void setUp() {
        service = new GitLabFrontendRouteSourceTraversalService(repositoryPort);
    }

    @Test
    void shouldTraverseTargetedCrmRouteSourcesThroughAliasesBarrelsAndLazyBoundaries() {
        var files = crmGraph();
        stubFiles(files);

        var result = service.traverse(scope(), root(), GitLabFrontendGraphLimits.defaults());

        assertThat(result.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.READY);
        assertThat(result.routeCollections())
                .extracting(GitLabFrontendRouteSourceTraversalResult.RouteCollection::sourcePath)
                .containsExactlyInAnyOrder(
                        "libs/crm/navigation/root.routes.ts",
                        "libs/crm/navigation/contact.children.ts",
                        "libs/crm/navigation/valuation/valuation.routes.ts",
                        "libs/crm/navigation/customer/customer.routes.ts"
                );
        assertThat(result.routeCollections())
                .flatExtracting(collection -> collection.parsed().routes())
                .extracting(AngularRouteSourceParser.ParsedRoute::fullPath)
                .contains(
                        "/contacts",
                        "/contacts/new",
                        "/valuation",
                        "/valuation/calculate",
                        "/customer",
                        "/customer/profile"
                );
        assertThat(result.componentTargets())
                .extracting(GitLabFrontendRouteSourceTraversalResult.ComponentTarget::symbol)
                .contains(
                        "CrmContactShellComponent",
                        "CrmContactCreateComponent",
                        "CrmValuationComponent",
                        "CrmCustomerComponent"
                );
        assertThat(result.routeCollections())
                .extracting(GitLabFrontendRouteSourceTraversalResult.RouteCollection::relation)
                .contains(
                        GitLabFrontendRouteGraphEdgeKind.CHILDREN,
                        GitLabFrontendRouteGraphEdgeKind.LOAD_CHILDREN
                );
        assertThat(result.coverage().sourceReadCount()).isLessThan(100);
        verify(repositoryPort, never()).listRepositoryFiles(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldKeepCodeSearchPrefixesAsAHardBoundary() {
        stubFiles(Map.of(
                "apps/crm-agent/src/app/app.config.ts", """
                        import { CRM_ROUTES } from '@crm/navigation';
                        export const CRM_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                        """
        ));
        var scoped = new GitLabFrontendRepositoryScope(
                "crm-platform", "crm-agent-frontend", "main", List.of("apps/crm-agent")
        );

        var result = service.traverse(scoped, root(), GitLabFrontendGraphLimits.defaults());

        assertThat(result.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.IMPORT_TARGET_NOT_FOUND);
        verify(repositoryPort, never()).readFile(
                "crm-platform", "crm-agent-frontend", "main",
                "libs/crm/navigation/index.ts",
                GitLabFrontendGraphLimits.defaults().maxFileCharacters()
        );
        verify(repositoryPort, never()).listRepositoryFiles(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldStopOnCrmReExportCycleWithoutRepositoryInventoryFallback() {
        var files = new LinkedHashMap<String, String>();
        files.put("tsconfig.base.json", crmTsconfig());
        files.put("apps/crm-agent/src/app/app.config.ts", """
                import { CRM_ROUTES } from '@crm/navigation';
                export const CRM_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                """);
        files.put("libs/crm/navigation/index.ts", "export * from './secondary';");
        files.put("libs/crm/navigation/secondary.ts", "export * from './index';");
        stubFiles(files);

        var result = service.traverse(scope(), root(), GitLabFrontendGraphLimits.defaults());

        assertThat(result.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.IMPORT_CYCLE_DETECTED);
        verify(repositoryPort, never()).listRepositoryFiles(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldReportAmbiguousCrmModuleInsteadOfSelectingAFileCandidate() {
        var files = new LinkedHashMap<String, String>();
        files.put("tsconfig.base.json", crmTsconfig());
        files.put("apps/crm-agent/src/app/app.config.ts", """
                import { CRM_ROUTES } from '@crm/navigation';
                export const CRM_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                """);
        files.put("libs/crm/navigation.ts", "export const CRM_ROUTES = [{ path: 'contacts' }];");
        files.put("libs/crm/navigation/index.ts", "export const CRM_ROUTES = [{ path: 'customers' }];");
        stubFiles(files);

        var result = service.traverse(scope(), root(), GitLabFrontendGraphLimits.defaults());

        assertThat(result.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.IMPORT_TARGET_AMBIGUOUS);
        verify(repositoryPort, never()).listRepositoryFiles(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldEnforceTargetedSourceReadBudget() {
        stubFiles(crmGraph());
        var limits = new GitLabFrontendGraphLimits(10, 400, 80, 3, 500, 12, 5, 40, 50_000, 500_000);

        var result = service.traverse(scope(), root(), limits);

        assertThat(result.coverage().limitReached()).isTrue();
        assertThat(result.coverage().sourceReadCount()).isEqualTo(3);
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.SOURCE_READ_LIMIT_REACHED);
        verify(repositoryPort, never()).listRepositoryFiles(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldEnforceCrmImportDepthBudget() {
        var files = new LinkedHashMap<String, String>();
        files.put("tsconfig.base.json", crmTsconfig());
        files.put("apps/crm-agent/src/app/app.config.ts", """
                import { CRM_ROUTES } from '@crm/navigation';
                export const CRM_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                """);
        files.put("libs/crm/navigation/index.ts", "export * from './level-one';");
        files.put("libs/crm/navigation/level-one.ts", "export * from './level-two';");
        files.put("libs/crm/navigation/level-two.ts",
                "export const CRM_ROUTES = [{ path: 'contacts' }];");
        stubFiles(files);
        var limits = new GitLabFrontendGraphLimits(10, 400, 80, 300, 500, 1, 5, 40, 50_000, 500_000);

        var result = service.traverse(scope(), root(), limits);

        assertThat(result.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(result.coverage().limitReached()).isTrue();
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.IMPORT_DEPTH_LIMIT_REACHED);
    }

    @Test
    void shouldRejectOversizedCrmSourceWithoutParsingPartialTypeScript() {
        var files = new LinkedHashMap<String, String>();
        files.put("apps/crm-agent/src/app/app.config.ts", """
                import { CRM_ROUTES } from './crm.routes';
                export const CRM_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                """);
        files.put("apps/crm-agent/src/app/crm.routes.ts", """
                export const CRM_ROUTES = [
                  { path: 'contacts' },
                  { path: 'customers' },
                  { path: 'opportunities' }
                ];
                """);
        stubFiles(files);
        var limits = new GitLabFrontendGraphLimits(10, 400, 80, 300, 500, 12, 5, 40, 100, 500_000);

        var result = service.traverse(scope(), root(), limits);

        assertThat(result.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(result.coverage().limitReached()).isTrue();
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.FILE_CHARACTER_LIMIT_REACHED);
    }

    @Test
    void shouldEnforceCrmRouteFileBudgetAtLazyBoundary() {
        stubFiles(crmGraph());
        var limits = new GitLabFrontendGraphLimits(10, 400, 1, 300, 500, 12, 5, 40, 50_000, 500_000);

        var result = service.traverse(scope(), root(), limits);

        assertThat(result.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.PARTIAL);
        assertThat(result.coverage().limitReached()).isTrue();
        assertThat(result.coverage().visitedRouteFileCount()).isEqualTo(1);
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.ROUTE_FILE_LIMIT_REACHED);
    }

    private void stubFiles(Map<String, String> files) {
        when(repositoryPort.readFile(
                anyString(), anyString(), anyString(), anyString(), anyInt()
        )).thenAnswer(invocation -> {
            var path = invocation.getArgument(3, String.class);
            var content = files.get(path);
            return content != null
                    ? new GitLabRepositoryFileContent(
                            "crm-platform", "crm-agent-frontend", "main", path, content, false
                    )
                    : null;
        });
    }

    private Map<String, String> crmGraph() {
        var files = new LinkedHashMap<String, String>();
        files.put("tsconfig.base.json", crmTsconfig());
        files.put("apps/crm-agent/src/app/app.config.ts", """
                import { provideRouter } from '@angular/router';
                import { CRM_ROUTES } from '@crm/navigation';
                export const CRM_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                """);
        files.put("libs/crm/navigation/index.ts", "export { CRM_ROUTES } from './root.routes';");
        files.put("libs/crm/navigation/root.routes.ts", """
                import { CRM_PATHS } from '@crm/model/routes.model';
                import { CrmContactShellComponent } from './views/contact-shell.component';
                import { CRM_CONTACT_CHILDREN } from './contact.children';
                export const CRM_ROUTES = [
                  { path: CRM_PATHS.contacts, component: CrmContactShellComponent, children: CRM_CONTACT_CHILDREN },
                  { path: 'valuation', loadChildren: () => import('./valuation/valuation.routes')
                      .then(({ CRM_VALUATION_ROUTES }) => CRM_VALUATION_ROUTES) },
                  { path: 'customer', loadChildren: () => import('./customer/customer.routes')
                      .then(module => module.CRM_CUSTOMER_ROUTES) }
                ];
                """);
        files.put("libs/crm/model/routes.model.ts", """
                export const CRM_PATHS = { contacts: 'contacts' } as const;
                """);
        files.put("libs/crm/navigation/contact.children.ts", """
                export const CRM_CONTACT_CHILDREN = [
                  { path: 'new', loadComponent: () => import('./views/contact-create.component')
                      .then(module => module.CrmContactCreateComponent) }
                ];
                """);
        files.put("libs/crm/navigation/valuation/valuation.routes.ts", """
                import { CrmValuationComponent } from './valuation.component';
                export const CRM_VALUATION_ROUTES = [{ path: 'calculate', component: CrmValuationComponent }];
                """);
        files.put("libs/crm/navigation/customer/customer.routes.ts", """
                import { CrmCustomerComponent } from './customer.component';
                export const CRM_CUSTOMER_ROUTES = [{ path: 'profile', component: CrmCustomerComponent }];
                """);
        files.put("libs/crm/navigation/views/contact-shell.component.ts",
                "export class CrmContactShellComponent {}");
        files.put("libs/crm/navigation/views/contact-create.component.ts",
                "export class CrmContactCreateComponent {}");
        files.put("libs/crm/navigation/valuation/valuation.component.ts",
                "export class CrmValuationComponent {}");
        files.put("libs/crm/navigation/customer/customer.component.ts",
                "export class CrmCustomerComponent {}");
        return files;
    }

    private String crmTsconfig() {
        return """
                {
                  // Synthetic CRM aliases only.
                  "compilerOptions": {
                    "baseUrl": ".",
                    "paths": { "@crm/*": ["libs/crm/*"] }
                  }
                }
                """;
    }

    private GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope(
                "crm-platform", "crm-agent-frontend", "main", List.of()
        );
    }

    private GitLabFrontendBootstrapRoot root() {
        return new GitLabFrontendBootstrapRoot(
                "bootstrap-crm",
                "bootstrapApplication",
                source("apps/crm-agent/src/main.ts", "bootstrapApplication"),
                source("apps/crm-agent/src/app/app.config.ts", "CRM_CONFIG"),
                "provideRouter",
                source("apps/crm-agent/src/app/app.config.ts", "provideRouter"),
                "CRM_ROUTES"
        );
    }

    private GitLabFrontendSourceReference source(String path, String symbol) {
        return new GitLabFrontendSourceReference(path, symbol, 1, 1);
    }
}
