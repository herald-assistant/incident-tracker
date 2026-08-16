package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitLabFrontendRouteGraphDiscoveryServiceTest {

    @Mock
    private GitLabRepositoryPort repositoryPort;

    private GitLabFrontendRouteGraphDiscoveryService service;

    @BeforeEach
    void setUp() {
        var bootstrap = new GitLabFrontendBootstrapDiscoveryService(repositoryPort);
        var traversal = new GitLabFrontendRouteSourceTraversalService(repositoryPort);
        service = new GitLabFrontendRouteGraphDiscoveryService(bootstrap, traversal, repositoryPort);
        when(repositoryPort.branchExists("crm-platform", "crm-agent-frontend", "main")).thenReturn(true);
        when(repositoryPort.resolveRevision("crm-platform", "crm-agent-frontend", "main"))
                .thenReturn(new GitLabRepositoryRevision(
                        "crm-platform",
                        "crm-agent-frontend",
                        "main",
                        "crm-ui-commit-20260816",
                        "2026-08-16T09:30:00.000Z"
                ));
    }

    @Test
    void shouldBuildDeterministicCrmGraphWithEffectiveConfigurationChains() {
        var files = crmGraph();
        stubRepository(files);

        var graph = service.discover(scope(), GitLabFrontendGraphLimits.defaults());
        var reformatted = new LinkedHashMap<>(files);
        reformatted.put("apps/crm-agent/src/main.ts", "// Synthetic CRM formatting change.\n"
                + files.get("apps/crm-agent/src/main.ts"));
        reformatted.put("apps/crm-agent/src/app/crm.routes.ts", "\n\n// CRM route catalog.\n"
                + files.get("apps/crm-agent/src/app/crm.routes.ts"));
        stubRepository(reformatted);
        var repeated = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(graph.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.READY);
        assertThat(graph.sourceRevision().commitId()).isEqualTo("crm-ui-commit-20260816");
        assertThat(graph.nodes()).hasSize(6);
        assertThat(graph.rootNodeIds()).hasSize(1);
        assertThat(graph.nodes()).extracting(GitLabFrontendRouteNode::nodeId)
                .containsExactlyElementsOf(repeated.nodes().stream()
                        .map(GitLabFrontendRouteNode::nodeId)
                        .toList());

        var contactScreens = graph.nodes().stream()
                .filter(node -> "/contacts/:contactId".equals(node.routePattern()))
                .filter(node -> node.screen() != null)
                .toList();
        assertThat(contactScreens).hasSize(2);
        assertThat(contactScreens).extracting(GitLabFrontendRouteNode::outlet)
                .containsExactlyInAnyOrder("primary", "drawer");
        assertThat(contactScreens).extracting(node -> node.screen().screenId()).doesNotHaveDuplicates();

        var primaryContact = contactScreens.stream()
                .filter(node -> "primary".equals(node.outlet()))
                .findFirst()
                .orElseThrow();
        var contactChain = graph.effectiveRouteChains().stream()
                .filter(chain -> chain.screen().equals(primaryContact.screen()))
                .findFirst()
                .orElseThrow();
        assertThat(contactChain.segments()).hasSize(2);
        assertThat(contactChain.routeParameters()).containsExactly("contactId");
        assertThat(contactChain.segments().get(0).pathSegment()).isEmpty();
        assertThat(contactChain.segments().get(0).configuration())
                .extracting(GitLabFrontendRouteConfiguration::kind)
                .contains(
                        GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE_CHILD,
                        GitLabFrontendRouteConfigurationKind.CAN_MATCH,
                        GitLabFrontendRouteConfigurationKind.DATA,
                        GitLabFrontendRouteConfigurationKind.PROVIDERS
                );
        assertThat(contactChain.segments().get(1).configuration())
                .extracting(GitLabFrontendRouteConfiguration::kind)
                .contains(
                        GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE,
                        GitLabFrontendRouteConfigurationKind.CAN_DEACTIVATE,
                        GitLabFrontendRouteConfigurationKind.RESOLVE,
                        GitLabFrontendRouteConfigurationKind.TITLE
                );
        assertThat(primaryContact.configuration().stream()
                .filter(configuration -> configuration.kind() == GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE)
                .findFirst().orElseThrow().referencedSymbols())
                .containsExactly("CrmContactGuard");

        var lazyScreen = graph.nodes().stream()
                .filter(node -> node.screen() != null)
                .filter(node -> "CrmValuationComponent".equals(node.screen().viewTarget().symbol()))
                .findFirst()
                .orElseThrow();
        var lazyChain = graph.effectiveRouteChains().stream()
                .filter(chain -> chain.screen().equals(lazyScreen.screen()))
                .findFirst()
                .orElseThrow();
        assertThat(lazyChain.segments()).hasSize(3);
        assertThat(lazyScreen.routePattern()).isEqualTo("/valuation");
        assertThat(lazyScreen.lazyBoundary()).isTrue();
        assertThat(graph.nodes()).filteredOn(node -> "/valuation".equals(node.routePattern()))
                .anySatisfy(node -> assertThat(node.configuration())
                        .extracting(GitLabFrontendRouteConfiguration::kind)
                        .contains(GitLabFrontendRouteConfigurationKind.CAN_LOAD));
        assertThat(lazyScreen.configuration())
                .extracting(GitLabFrontendRouteConfiguration::kind)
                .contains(GitLabFrontendRouteConfigurationKind.RUN_GUARDS_AND_RESOLVERS);
        assertThat(graph.edges()).extracting(GitLabFrontendRouteGraphEdge::kind)
                .contains(
                        GitLabFrontendRouteGraphEdgeKind.ROOT_ROUTES,
                        GitLabFrontendRouteGraphEdgeKind.CHILDREN,
                        GitLabFrontendRouteGraphEdgeKind.LOAD_CHILDREN,
                        GitLabFrontendRouteGraphEdgeKind.COMPONENT,
                        GitLabFrontendRouteGraphEdgeKind.LOAD_COMPONENT
                );

        assertThat(graph.nodes()).anySatisfy(node -> {
            assertThat(node.kind()).isEqualTo(GitLabFrontendRouteNodeKind.REDIRECT);
            assertThat(node.redirectTarget()).isEqualTo("contacts");
            assertThat(node.configuration()).extracting(GitLabFrontendRouteConfiguration::kind)
                    .contains(GitLabFrontendRouteConfigurationKind.PATH_MATCH);
        });
        verify(repositoryPort, never()).listRepositoryFiles(anyString(), anyString(), anyString(), anyString());
        verify(repositoryPort, never()).readFileMetadata(
                anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shouldStopTheCrmCatalogAtResolvedComponentTargets() {
        stubRepository(crmGraph());

        var graph = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(graph.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.READY);
        assertThat(graph.nodes()).filteredOn(node -> node.screen() != null)
                .allSatisfy(node -> assertThat(node.viewTarget().sourcePath()).endsWith(".component.ts"));
        verify(repositoryPort, never()).readFile(
                "crm-platform", "crm-agent-frontend", "main",
                "libs/crm/data/crm-contact.client.ts",
                GitLabFrontendGraphLimits.defaults().maxFileCharacters()
        );
    }

    @Test
    void shouldReturnBlockedGraphWhenNoCrmBootstrapRootCanBeProven() {
        when(repositoryPort.searchRepositoryFilesByContent(
                anyString(), anyString(), anyString(), anyList(), anyInt()
        )).thenReturn(List.of());

        var graph = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(graph.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(graph.bootstrapRoot()).isNull();
        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_ROOT_NOT_FOUND);
        verify(repositoryPort, never()).listRepositoryFiles(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldReportUnresolvedCrmRevisionWithoutUsingBootstrapFileMetadata() {
        stubRepository(crmGraph());
        when(repositoryPort.resolveRevision("crm-platform", "crm-agent-frontend", "main"))
                .thenThrow(new IllegalStateException("synthetic CRM revision lookup failure"));

        var graph = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(graph.sourceRevision().commitId()).isNull();
        assertThat(graph.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.SOURCE_REVISION_UNRESOLVED);
        verify(repositoryPort, never()).readFileMetadata(
                anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shouldKeepAnUnresolvedCrmViewAsATypedGraphEdge() {
        var files = new LinkedHashMap<String, String>();
        files.put("apps/crm-agent/src/main.ts", """
                import { bootstrapApplication } from '@angular/platform-browser';
                import { CRM_CONFIG } from './app/app.config';
                bootstrapApplication(CrmAgentComponent, CRM_CONFIG);
                """);
        files.put("apps/crm-agent/src/app/app.config.ts", """
                import { provideRouter } from '@angular/router';
                import { CRM_ROUTES } from './crm.routes';
                export const CRM_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                """);
        files.put("apps/crm-agent/src/app/crm.routes.ts", """
                export const CRM_ROUTES = [{
                  path: 'contacts',
                  loadComponent: () => import('./missing-contact.component')
                    .then(module => module.CrmMissingContactComponent)
                }];
                """);
        stubRepository(files);

        var graph = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(graph.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.PARTIAL);
        assertThat(graph.nodes()).singleElement().satisfies(node -> {
            assertThat(node.kind()).isEqualTo(GitLabFrontendRouteNodeKind.UNRESOLVED);
            assertThat(node.status()).isEqualTo(GitLabFrontendDiscoveryStatus.PARTIAL);
        });
        assertThat(graph.edges())
                .filteredOn(edge -> edge.kind() == GitLabFrontendRouteGraphEdgeKind.LOAD_COMPONENT)
                .singleElement()
                .extracting(GitLabFrontendRouteGraphEdge::status)
                .isEqualTo(GitLabFrontendRouteGraphEdgeStatus.NOT_FOUND);
        assertThat(graph.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.IMPORT_TARGET_NOT_FOUND);
    }

    private void stubRepository(Map<String, String> files) {
        when(repositoryPort.searchRepositoryFilesByContent(
                anyString(), anyString(), anyString(), anyList(), anyInt()
        )).thenReturn(List.of(
                candidate("apps/crm-agent/src/main.ts"),
                candidate("apps/crm-agent/src/app/app.config.ts")
        ));
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

    private GitLabRepositoryFileCandidate candidate(String path) {
        return new GitLabRepositoryFileCandidate(
                "crm-platform", "crm-agent-frontend", "main", path, "synthetic CRM bootstrap", 100
        );
    }

    private Map<String, String> crmGraph() {
        var files = new LinkedHashMap<String, String>();
        files.put("apps/crm-agent/src/main.ts", """
                import { bootstrapApplication } from '@angular/platform-browser';
                import { CRM_CONFIG } from './app/app.config';
                import { CrmAgentComponent } from './app/crm-agent.component';
                bootstrapApplication(CrmAgentComponent, CRM_CONFIG);
                """);
        files.put("apps/crm-agent/src/app/app.config.ts", """
                import { provideRouter } from '@angular/router';
                import { CRM_ROUTES } from './crm.routes';
                export const CRM_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                """);
        files.put("apps/crm-agent/src/app/crm.routes.ts", """
                import { CrmContactComponent } from './contact.component';
                import { CrmContactDrawerComponent } from './contact-drawer.component';
                export const CRM_ROUTES = [{
                  path: '',
                  canActivateChild: [CrmSessionGuard],
                  canMatch: [CrmWorkspaceGuard],
                  data: { area: 'customer-relations' },
                  providers: [provideCrmScope()],
                  children: [
                    {
                      path: 'contacts/:contactId',
                      title: 'Contact profile',
                      canActivate: [CrmContactGuard],
                      canDeactivate: [CrmUnsavedContactGuard],
                      resolve: { contact: CrmContactResolver },
                      component: CrmContactComponent
                    },
                    {
                      path: 'contacts/:contactId',
                      outlet: 'drawer',
                      component: CrmContactDrawerComponent
                    },
                    { path: 'legacy', redirectTo: 'contacts', pathMatch: 'full' },
                    {
                      path: 'valuation',
                      canLoad: [CrmValuationLoadGuard],
                      loadChildren: () => import('./valuation.routes')
                        .then(({ CRM_VALUATION_ROUTES }) => CRM_VALUATION_ROUTES)
                    }
                  ]
                }];
                """);
        files.put("apps/crm-agent/src/app/valuation.routes.ts", """
                export const CRM_VALUATION_ROUTES = [{
                  path: '',
                  loadComponent: () => import('./valuation.component')
                    .then(module => module.CrmValuationComponent),
                  runGuardsAndResolvers: 'paramsChange'
                }];
                """);
        files.put("apps/crm-agent/src/app/contact.component.ts", """
                import { CrmContactClient } from '../../../libs/crm/data/crm-contact.client';
                export class CrmContactComponent {}
                """);
        files.put("apps/crm-agent/src/app/contact-drawer.component.ts",
                "export class CrmContactDrawerComponent {}");
        files.put("apps/crm-agent/src/app/valuation.component.ts",
                "export class CrmValuationComponent {}");
        return files;
    }

    private GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope(
                "crm-platform", "crm-agent-frontend", "main", List.of()
        );
    }
}
