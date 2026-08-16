package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GitLabFrontendScreenGraphContextServiceTest {

    @Test
    void shouldReadOnlySelectedSyntheticCrmRouteAndComponentDependenciesWithoutRepositoryInventory() {
        var graphDiscovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        var repository = mock(GitLabRepositoryPort.class);
        var graph = graph();
        when(graphDiscovery.discover(any(), any())).thenReturn(graph);
        var files = files();
        when(repository.readFile(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    var path = invocation.getArgument(3, String.class);
                    var content = files.get(path);
                    if (content == null) {
                        throw new IllegalArgumentException("Synthetic CRM fixture file not found: " + path);
                    }
                    return new GitLabRepositoryFileContent(
                            "synthetic-crm", "crm-agent-portal", "main", path, content, false
                    );
                });
        var service = new GitLabFrontendScreenGraphContextService(graphDiscovery, repository);

        var result = service.build(new GitLabFrontendScreenGraphContextRequest(
                graph.scope(), graph.nodes().get(1).screen().screenId(), "crm-commit-20260816",
                GitLabFrontendGraphLimits.defaults()
        ));

        assertThat(result.sourceFiles()).extracting(GitLabFrontendSourceFile::path)
                .contains(
                        "apps/crm-agent/src/app/app.routes.ts",
                        "apps/crm-agent/src/app/preferences/crm-preferences.component.ts",
                        "apps/crm-agent/src/app/preferences/crm-preferences.component.html",
                        "apps/crm-agent/src/app/preferences/crm-preferences.component.scss",
                        "apps/crm-agent/src/app/preferences/crm-preferences.service.ts",
                        "apps/crm-agent/src/app/preferences/state/crm-preferences.selectors.ts",
                        "apps/crm-agent/src/app/auth/crm-contact.guard.ts"
                );
        assertThat(result.sourceFiles()).filteredOn(file -> file.path().endsWith("crm-contact.guard.ts"))
                .singleElement().satisfies(file -> assertThat(file.roles())
                        .contains(GitLabFrontendSourceRole.AUTHORIZATION));
        assertThat(result.technicalSignals()).extracting(GitLabFrontendTechnicalSignal::kind)
                .contains(
                        GitLabFrontendTechnicalSignalKind.REACTIVE_FORM,
                        GitLabFrontendTechnicalSignalKind.NGRX_STORE,
                        GitLabFrontendTechnicalSignalKind.HTTP_CLIENT,
                        GitLabFrontendTechnicalSignalKind.AUTH_GUARD
                );
        assertThat(result.contextLimitReached()).isFalse();
        verify(repository, never()).listRepositoryFiles(anyString(), anyString(), anyString(), any());
        verify(repository, never()).searchRepositoryFilesByContent(anyString(), anyString(), anyString(), anyList(), anyInt());
    }

    @Test
    void shouldRejectStaleRevisionBeforeReadingSelectedScreenFiles() {
        var graphDiscovery = mock(GitLabFrontendRouteGraphDiscoveryService.class);
        var repository = mock(GitLabRepositoryPort.class);
        when(graphDiscovery.discover(any(), any())).thenReturn(graph());
        var service = new GitLabFrontendScreenGraphContextService(graphDiscovery, repository);

        assertThatThrownBy(() -> service.build(new GitLabFrontendScreenGraphContextRequest(
                graph().scope(), graph().nodes().get(1).screen().screenId(), "crm-stale-commit",
                GitLabFrontendGraphLimits.defaults()
        )))
                .isInstanceOf(GitLabFrontendDiscoveryException.class)
                .extracting(exception -> ((GitLabFrontendDiscoveryException) exception).code())
                .isEqualTo("FRONTEND_SOURCE_REVISION_CHANGED");
        verifyNoInteractions(repository);
    }

    private GitLabFrontendRouteGraph graph() {
        var scope = new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "main", List.of("apps/crm-agent")
        );
        var routeSource = new GitLabFrontendSourceReference(
                "apps/crm-agent/src/app/app.routes.ts", "CRM_ROUTES", 4, 14
        );
        var parent = new GitLabFrontendRouteNode(
                "route-crm-root", null, null, "CRM", "", "/", "primary",
                GitLabFrontendRouteNodeKind.ROUTE, GitLabFrontendDiscoveryStatus.RESOLVED,
                false, List.of(), null, null, null,
                List.of(new GitLabFrontendRouteConfiguration(
                        GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE_CHILD, "canActivateChild",
                        List.of("CrmSessionGuard"), null, GitLabFrontendDiscoveryStatus.RESOLVED,
                        routeSource, List.of()
                )), routeSource, List.of()
        );
        var target = new GitLabFrontendRouteTarget(
                "CrmPreferencesComponent", "apps/crm-agent/src/app/preferences/crm-preferences.component.ts"
        );
        var identity = new GitLabFrontendScreenIdentity(
                "screen-crm-preferences", "route-crm-preferences", "/contacts/:contactId/preferences",
                "primary", target
        );
        var screen = new GitLabFrontendRouteNode(
                identity.routeNodeId(), parent.nodeId(), identity, "Contact preferences", "contacts/:contactId/preferences",
                identity.routePattern(), "primary", GitLabFrontendRouteNodeKind.SCREEN,
                GitLabFrontendDiscoveryStatus.RESOLVED, true, List.of("contactId"), target, null, null,
                List.of(new GitLabFrontendRouteConfiguration(
                        GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE, "canActivate", List.of("CrmContactGuard"),
                        null, GitLabFrontendDiscoveryStatus.RESOLVED, routeSource, List.of()
                )), routeSource, List.of()
        );
        var chain = new GitLabFrontendEffectiveRouteChain(
                identity,
                List.of(
                        new GitLabFrontendRouteChainSegment(
                                parent.nodeId(), parent.pathSegment(), parent.routePattern(), parent.outlet(), parent.configuration(), routeSource
                        ),
                        new GitLabFrontendRouteChainSegment(
                                screen.nodeId(), screen.pathSegment(), screen.routePattern(), screen.outlet(), screen.configuration(), routeSource
                        )
                ), List.of("contactId")
        );
        var bootstrap = new GitLabFrontendBootstrapRoot(
                "bootstrap-crm", "CrmAppComponent",
                new GitLabFrontendSourceReference("apps/crm-agent/src/main.ts", "CrmAppComponent", 1, 4),
                null, "provideRouter", new GitLabFrontendSourceReference(
                        "apps/crm-agent/src/app/app.config.ts", "provideRouter", 3, 3
                ), "CRM_ROUTES"
        );
        return new GitLabFrontendRouteGraph(
                scope, new GitLabFrontendSourceRevision("main", "crm-commit-20260816"), bootstrap,
                List.of(parent.nodeId()), List.of(parent, screen), List.of(), List.of(chain), List.of(),
                new GitLabFrontendGraphCoverage(
                        GitLabFrontendCoverageStatus.READY, 2, 1, 6, 2, 0, false, List.of()
                ), List.of()
        );
    }

    private LinkedHashMap<String, String> files() {
        var files = new LinkedHashMap<String, String>();
        files.put("apps/crm-agent/src/app/app.routes.ts", """
                import { CrmContactGuard } from './auth/crm-contact.guard';
                export const CRM_ROUTES = [{ path: 'contacts/:contactId/preferences' }];
                """);
        files.put("apps/crm-agent/src/app/auth/crm-contact.guard.ts", """
                export const CrmContactGuard = () => inject(CrmPermissionService).hasRole('CRM_CONTACT_VIEW');
                """);
        files.put("apps/crm-agent/src/app/preferences/crm-preferences.component.ts", """
                import { CrmPreferencesService } from './crm-preferences.service';
                import { selectCrmPreferences } from './state/crm-preferences.selectors';
                @Component({
                  templateUrl: './crm-preferences.component.html',
                  styleUrls: ['./crm-preferences.component.scss']
                })
                export class CrmPreferencesComponent {
                  readonly form = new FormGroup({ channel: new FormControl('') });
                  readonly preferences$ = this.store.select(selectCrmPreferences);
                }
                """);
        files.put("apps/crm-agent/src/app/preferences/crm-preferences.service.ts", """
                export class CrmPreferencesService {
                  private readonly http = inject(HttpClient);
                  load() { return this.http.get('/api/synthetic-crm/preferences'); }
                }
                """);
        files.put("apps/crm-agent/src/app/preferences/state/crm-preferences.selectors.ts", """
                import { createSelector } from '@ngrx/store';
                export const selectCrmPreferences = createSelector(selectCrmState, state => state.preferences);
                """);
        files.put("apps/crm-agent/src/app/preferences/crm-preferences.component.html", """
                <form [formGroup]="form"><input formControlName="channel"></form>
                """);
        files.put("apps/crm-agent/src/app/preferences/crm-preferences.component.scss", ":host { display: block; }");
        return files;
    }
}
