package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitLabAngularRouteBranchSliceServiceTest {

    @Mock
    private GitLabFrontendRouteGraphDiscoveryService routeGraphDiscoveryService;

    @Mock
    private GitLabRepositoryPort repositoryPort;

    @Test
    void shouldKeepOnlySelectedSyntheticCrmRouteBranchAndExposeChildrenAsFrontier() {
        var source = """
                import { Routes } from '@angular/router';
                import { CrmRole } from './security/crm-role.model';
                import { CrmAccessGuard } from './security/crm-access.guard';
                import { CrmCustomerEditorComponent } from './customer/crm-customer-editor.component';
                import { CrmCustomerHistoryComponent } from './customer/crm-customer-history.component';
                import { CrmInternalAuditComponent } from './internal/crm-internal-audit.component';

                const CRM_BASE_ROLES: CrmRole[] = ['CRM_CONTACT_READ'];
                const CRM_EXTENDED_ROLES: CrmRole[] = [...CRM_BASE_ROLES, 'CRM_CONTACT_EDIT'];

                export const crmRoutes: Routes = [
                  {
                    path: 'customers',
                    canActivate: [CrmAccessGuard],
                    data: { AUTH_ROLES: CRM_EXTENDED_ROLES },
                    children: [
                      {
                        path: ':customerId',
                        component: CrmCustomerEditorComponent,
                        children: [
                          { path: 'history', component: CrmCustomerHistoryComponent },
                          { path: 'internal-audit', component: CrmInternalAuditComponent }
                        ]
                      },
                      { path: 'internal-audit', component: CrmInternalAuditComponent }
                    ]
                  }
                ];
                """;
        var scope = new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "main", List.of("apps/crm-agent")
        );
        var path = "apps/crm-agent/src/app/crm.routes.ts";
        var parent = routeNode(
                "route-crm-customers", null, null, "/customers", "customers", path,
                lineOf(source, "{\n    path: 'customers'"), null
        );
        var selected = routeNode(
                "route-crm-customer-editor", parent.nodeId(), "screen-crm-customer-editor",
                "/customers/:customerId", ":customerId", path,
                lineOf(source, "{\n        path: ':customerId'"), "CrmCustomerEditorComponent"
        );
        var history = routeNode(
                "route-crm-customer-history", selected.nodeId(), "screen-crm-customer-history",
                "/customers/:customerId/history", "history", path,
                lineOf(source, "{ path: 'history'"), "CrmCustomerHistoryComponent"
        );
        var audit = routeNode(
                "route-crm-customer-audit", selected.nodeId(), "screen-crm-customer-audit",
                "/customers/:customerId/internal-audit", "internal-audit", path,
                lineOf(source, "{ path: 'internal-audit'"), "CrmInternalAuditComponent"
        );
        var chain = new GitLabFrontendEffectiveRouteChain(
                selected.screen(),
                List.of(segment(parent), segment(selected)),
                List.of("customerId")
        );
        var graph = new GitLabFrontendRouteGraph(
                scope, new GitLabFrontendSourceRevision("main", "crm-ui-commit-20260819"),
                mock(GitLabFrontendBootstrapRoot.class), List.of(parent.nodeId()),
                List.of(parent, selected, history, audit), List.of(), List.of(chain), List.of(),
                new GitLabFrontendGraphCoverage(
                        GitLabFrontendCoverageStatus.READY, 4, 1, 5, 1, 0, false, List.of()
                ), List.of()
        );
        when(routeGraphDiscoveryService.discover(scope, GitLabFrontendGraphLimits.defaults())).thenReturn(graph);
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), path, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), path, source, false
                ));

        var response = new GitLabAngularRouteBranchSliceService(routeGraphDiscoveryService, repositoryPort)
                .readBranchSlice(new GitLabAngularRouteBranchSliceRequest(
                        scope, selected.screen().screenId(), "crm-ui-commit-20260819", false, 24_000
                ));

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.files()).hasSize(1);
        assertThat(response.files().get(0).content())
                .contains(
                        "path: 'customers'",
                        "path: ':customerId'",
                        "CrmAccessGuard",
                        "const CRM_BASE_ROLES: CrmRole[]",
                        "const CRM_EXTENDED_ROLES: CrmRole[]",
                        "import { CrmRole } from './security/crm-role.model';"
                )
                .contains("sibling route entry omitted")
                .doesNotContain("CrmInternalAuditComponent");
        assertThat(response.files().get(0).includedLocalDeclarations())
                .containsExactly("CRM_BASE_ROLES", "CRM_EXTENDED_ROLES");
        assertThat(response.files().get(0).unresolvedSymbols()).isEmpty();
        assertThat(response.omittedSiblingRouteCount()).isEqualTo(3);
        assertThat(response.childRoutes()).extracting(GitLabAngularRouteChildReference::routePattern)
                .containsExactly(
                        "/customers/:customerId/history",
                        "/customers/:customerId/internal-audit"
                );
        assertThat(response.childRoutes()).allSatisfy(child -> {
            assertThat(child.kind()).isEqualTo(GitLabFrontendRouteNodeKind.SCREEN);
            assertThat(child.structural()).isFalse();
            assertThat(child.samePathAsParent()).isFalse();
            assertThat(child.hasChildren()).isFalse();
        });
        assertThat(response.returnedCharacters()).isLessThan(response.sourceCharacters());
        assertThat(response.savedCharacters()).isPositive();
    }

    @Test
    void shouldReportUnresolvedSyntheticCrmRouteDependencyInsteadOfReturningOk() {
        var source = """
                import { CrmCustomerComponent } from './customer/crm-customer.component';

                export const crmRoutes = [
                  {
                    path: 'customers',
                    canActivate: [CrmMissingAccessPolicy],
                    component: CrmCustomerComponent
                  }
                ];
                """;
        var scope = new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "main", List.of("apps/crm-agent")
        );
        var path = "apps/crm-agent/src/app/crm.routes.ts";
        var line = lineOf(source, "{\n    path: 'customers'");
        var target = new GitLabFrontendRouteTarget(
                "CrmCustomerComponent", "apps/crm-agent/src/app/customer/crm-customer.component.ts"
        );
        var screen = new GitLabFrontendScreenIdentity(
                "screen-crm-customers", "route-crm-customers", "/customers", "primary", target
        );
        var node = new GitLabFrontendRouteNode(
                screen.routeNodeId(), null, screen, "Crm customers", "customers", screen.routePattern(), "primary",
                GitLabFrontendRouteNodeKind.SCREEN, GitLabFrontendDiscoveryStatus.RESOLVED, false, List.of(),
                target, null, null,
                List.of(new GitLabFrontendRouteConfiguration(
                        GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE, "canActivate",
                        List.of("CrmMissingAccessPolicy"), "[CrmMissingAccessPolicy]",
                        GitLabFrontendDiscoveryStatus.RESOLVED,
                        new GitLabFrontendSourceReference(path, "crmRoutes", line, line), List.of()
                )),
                new GitLabFrontendSourceReference(path, "crmRoutes", line, line), List.of()
        );
        var chain = new GitLabFrontendEffectiveRouteChain(screen, List.of(segment(node)), List.of());
        var graph = new GitLabFrontendRouteGraph(
                scope, new GitLabFrontendSourceRevision("main", "crm-ui-commit-20260819"),
                mock(GitLabFrontendBootstrapRoot.class), List.of(node.nodeId()), List.of(node), List.of(),
                List.of(chain), List.of(),
                new GitLabFrontendGraphCoverage(
                        GitLabFrontendCoverageStatus.READY, 1, 1, 2, 0, 0, false, List.of()
                ), List.of()
        );
        when(routeGraphDiscoveryService.discover(scope, GitLabFrontendGraphLimits.defaults())).thenReturn(graph);
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), path, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), path, source, false
                ));

        var response = new GitLabAngularRouteBranchSliceService(routeGraphDiscoveryService, repositoryPort)
                .readBranchSlice(new GitLabAngularRouteBranchSliceRequest(
                        scope, screen.screenId(), "crm-ui-commit-20260819", false, 24_000
                ));

        assertThat(response.status()).isEqualTo("PARTIAL");
        assertThat(response.files()).singleElement().satisfies(file ->
                assertThat(file.unresolvedSymbols()).containsExactly("CrmMissingAccessPolicy")
        );
        assertThat(response.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(
                    GitLabFrontendGraphDiagnosticCode.SYMBOL_DEPENDENCY_UNRESOLVED
            );
            assertThat(diagnostic.source().path()).isEqualTo(path);
        });
    }

    private GitLabFrontendRouteNode routeNode(
            String nodeId,
            String parentId,
            String screenId,
            String routePattern,
            String pathSegment,
            String routeSourcePath,
            int line,
            String component
    ) {
        var target = component != null
                ? new GitLabFrontendRouteTarget(component, routeSourcePath.replace("crm.routes.ts", component + ".ts"))
                : null;
        var screen = screenId != null
                ? new GitLabFrontendScreenIdentity(screenId, nodeId, routePattern, "primary", target)
                : null;
        return new GitLabFrontendRouteNode(
                nodeId, parentId, screen, pathSegment, pathSegment, routePattern, "primary",
                screen != null ? GitLabFrontendRouteNodeKind.SCREEN : GitLabFrontendRouteNodeKind.ROUTE,
                GitLabFrontendDiscoveryStatus.RESOLVED, false,
                routePattern.contains(":customerId") ? List.of("customerId") : List.of(),
                target, null, null,
                pathSegment.equals("customers")
                        ? List.of(
                                new GitLabFrontendRouteConfiguration(
                                        GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE, "canActivate",
                                        List.of("CrmAccessGuard"), null, GitLabFrontendDiscoveryStatus.RESOLVED,
                                        new GitLabFrontendSourceReference(routeSourcePath, "crmRoutes", line, line), List.of()
                                ),
                                new GitLabFrontendRouteConfiguration(
                                        GitLabFrontendRouteConfigurationKind.DATA, "data",
                                        List.of("AUTH_ROLES", "CRM_EXTENDED_ROLES"),
                                        "{ AUTH_ROLES: CRM_EXTENDED_ROLES }",
                                        GitLabFrontendDiscoveryStatus.RESOLVED,
                                        new GitLabFrontendSourceReference(routeSourcePath, "crmRoutes", line, line), List.of()
                                )
                        )
                        : List.of(),
                new GitLabFrontendSourceReference(routeSourcePath, "crmRoutes", line, line), List.of()
        );
    }

    private GitLabFrontendRouteChainSegment segment(GitLabFrontendRouteNode node) {
        return new GitLabFrontendRouteChainSegment(
                node.nodeId(), node.pathSegment(), node.routePattern(), node.outlet(),
                node.configuration(), node.routeSource()
        );
    }

    private int lineOf(String source, String marker) {
        var offset = source.indexOf(marker);
        assertThat(offset).isGreaterThanOrEqualTo(0);
        return 1 + (int) source.substring(0, offset).chars().filter(character -> character == '\n').count();
    }
}
