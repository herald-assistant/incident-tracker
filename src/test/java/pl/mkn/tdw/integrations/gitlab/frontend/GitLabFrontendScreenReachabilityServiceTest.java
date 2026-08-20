package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabFrontendScreenReachabilityServiceTest {

    private static final String ROOT_PATH =
            "apps/synthetic-crm/src/app/customer/customer-page.component.ts";
    private static final String CHILD_PATH =
            "apps/synthetic-crm/src/app/customer/customer-form.component.ts";
    private static final String DIALOG_PATH =
            "apps/synthetic-crm/src/app/customer/customer-note-dialog.component.ts";
    private static final String DETACHED_PATH =
            "apps/synthetic-crm/src/app/customer/customer-detached-audit.component.ts";
    private static final String FACADE_PATH =
            "apps/synthetic-crm/src/app/customer/customer.facade.ts";
    private static final String RULES_PATH =
            "apps/synthetic-crm/src/app/customer/customer-rules.service.ts";
    private static final String API_PATH =
            "libs/synthetic-crm/data-access/src/lib/api/services/customer-controller.service.ts";

    @Test
    void shouldRenderUniversalSyntheticCrmBreadthFirstGraphWithoutDuplicatingDependencies() {
        var contextService = mock(GitLabFrontendScreenGraphContextService.class);
        var symbolSliceService = mock(GitLabTypeScriptSymbolSliceService.class);
        when(contextService.build(any())).thenReturn(context());
        when(symbolSliceService.readSymbolSlice(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, GitLabTypeScriptSymbolSliceRequest.class);
            return slice(request);
        });

        var result = new GitLabFrontendScreenReachabilityService(contextService, symbolSliceService)
                .build(request());

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.componentLevels()).extracting(GitLabFrontendReachabilityComponentLevel::depth)
                .containsExactly(0, 1);
        assertThat(result.componentLevels().get(0).components())
                .extracting(GitLabFrontendReachabilityComponent::symbol)
                .containsExactly("CrmCustomerPageComponent");
        assertThat(result.componentLevels().get(1).components())
                .extracting(GitLabFrontendReachabilityComponent::symbol)
                .containsExactly("CrmCustomerFormComponent", "CrmCustomerNoteDialogComponent");
        assertThat(result.edges()).extracting(GitLabFrontendReachabilityEdge::kind)
                .contains(GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD,
                        GitLabFrontendReachabilityEdgeKind.DYNAMIC_COMPONENT,
                        GitLabFrontendReachabilityEdgeKind.USES_DEPENDENCY,
                        GitLabFrontendReachabilityEdgeKind.DEPENDENCY_CALL);

        var facade = result.dependencies().stream()
                .filter(dependency -> "CrmCustomerFacade".equals(dependency.symbol()))
                .findFirst().orElseThrow();
        assertThat(facade.kind()).isEqualTo(GitLabFrontendReachabilityDependencyKind.FACADE);
        assertThat(facade.methods()).containsExactly("saveCustomer", "customer$");
        assertThat(facade.usedBy()).hasSize(2);
        assertThat(result.dependencies()).extracting(GitLabFrontendReachabilityDependency::symbol)
                .contains("CrmCustomerRulesService", "CrmCustomerControllerService")
                .doesNotContain("CrmUnusedService");
        assertThat(result.dependencies().stream()
                .filter(dependency -> "CrmCustomerControllerService".equals(dependency.symbol()))
                .findFirst().orElseThrow().kind())
                .isEqualTo(GitLabFrontendReachabilityDependencyKind.BACKEND_CLIENT);

        assertThat(result.unlinkedComponents())
                .extracting(GitLabFrontendReachabilityComponent::symbol)
                .containsExactly("CrmCustomerDetachedAuditComponent");
        assertThat(result.readableOutline())
                .contains("## Effective route chain", "### Depth 0", "### Depth 1", "[C1]",
                        "## Canonical dependencies", "[D1]", "CrmCustomerFacade")
                .doesNotContain("CrmUnusedService", "component-", "dependency-");
        assertThat(result.outlineCharacters()).isEqualTo(result.readableOutline().length());
        assertThat(result.sliceCharacters()).isPositive();
    }

    private GitLabFrontendScreenGraphContextRequest request() {
        return new GitLabFrontendScreenGraphContextRequest(
                scope(), "screen-synthetic-crm-customer", "synthetic-crm-revision-20260820",
                GitLabFrontendGraphLimits.defaults()
        );
    }

    private GitLabFrontendScreenGraphContext context() {
        var routeSource = new GitLabFrontendSourceReference(
                "apps/synthetic-crm/src/app/app.routes.ts", "syntheticCrmRoutes", 10, 20
        );
        var target = new GitLabFrontendRouteTarget("CrmCustomerPageComponent", ROOT_PATH);
        var identity = new GitLabFrontendScreenIdentity(
                "screen-synthetic-crm-customer", "route-synthetic-crm-customer",
                "/crm/customers/:customerId", "primary", target
        );
        var node = new GitLabFrontendRouteNode(
                identity.routeNodeId(), null, identity, "Synthetic customer", "customers/:customerId",
                identity.routePattern(), "primary", GitLabFrontendRouteNodeKind.SCREEN,
                GitLabFrontendDiscoveryStatus.RESOLVED, true, List.of("customerId"), target, null, null,
                List.of(new GitLabFrontendRouteConfiguration(
                        GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE, "canActivate",
                        List.of("syntheticCrmRoleGuard"), "[syntheticCrmRoleGuard]",
                        GitLabFrontendDiscoveryStatus.RESOLVED, routeSource, List.of()
                )), routeSource, List.of()
        );
        var chain = new GitLabFrontendEffectiveRouteChain(
                identity,
                List.of(new GitLabFrontendRouteChainSegment(
                        node.nodeId(), node.pathSegment(), node.routePattern(), node.outlet(),
                        node.configuration(), routeSource
                )),
                List.of("customerId")
        );
        var files = List.of(
                source(ROOT_PATH, GitLabFrontendSourceRole.VIEW_COMPONENT, """
                        import { CrmCustomerFacade } from './customer.facade';
                        import { CrmUnusedService } from './unused.service';
                        import { CrmCustomerNoteDialogComponent } from './customer-note-dialog.component';
                        @Component({
                          selector: 'crm-customer-page',
                          templateUrl: './customer-page.component.html'
                        })
                        export class CrmCustomerPageComponent {
                          constructor(
                            private readonly customerFacade: CrmCustomerFacade,
                            private readonly unusedService: CrmUnusedService,
                            private readonly dialog: SyntheticDialog
                          ) {}
                          saveCustomer(): void { this.customerFacade.saveCustomer(); }
                          openNote(): void { this.dialog.open(CrmCustomerNoteDialogComponent); }
                        }
                        """),
                source("apps/synthetic-crm/src/app/customer/customer-page.component.html",
                        GitLabFrontendSourceRole.TEMPLATE, """
                                <crm-customer-form
                                  [customer]="customerFacade.customer$ | async"
                                  (submitted)="saveCustomer()" />
                                <button (click)="openNote()">Note</button>
                                """),
                source(CHILD_PATH, GitLabFrontendSourceRole.CHILD_COMPONENT, """
                        import { CrmCustomerFacade } from './customer.facade';
                        import { CrmCustomerRulesService } from './customer-rules.service';
                        @Component({ selector: 'crm-customer-form', template: '<button (click)="submit()">Save</button>' })
                        export class CrmCustomerFormComponent {
                          constructor(
                            private readonly customerFacade: CrmCustomerFacade,
                            private readonly rules: CrmCustomerRulesService
                          ) {}
                          submit(): void { this.rules.validate(); this.customerFacade.customer$; }
                        }
                        """),
                source(DIALOG_PATH, GitLabFrontendSourceRole.CHILD_COMPONENT, """
                        @Component({ selector: 'crm-customer-note-dialog', template: '<p>Note</p>' })
                        export class CrmCustomerNoteDialogComponent {}
                        """),
                source(DETACHED_PATH, GitLabFrontendSourceRole.CHILD_COMPONENT, """
                        @Component({ selector: 'crm-detached-audit', template: '<p>Audit</p>' })
                        export class CrmCustomerDetachedAuditComponent {}
                        """),
                source(FACADE_PATH, GitLabFrontendSourceRole.STATE_MANAGEMENT, """
                        import { CrmCustomerControllerService } from '@synthetic-crm/data-access/src/lib/api/services/customer-controller.service';
                        export class CrmCustomerFacade {
                          constructor(private readonly customerApi: CrmCustomerControllerService) {}
                          readonly customer$ = of({ id: 'synthetic' });
                          saveCustomer(): void { this.customerApi.updateCustomer(); }
                        }
                        """),
                source(RULES_PATH, GitLabFrontendSourceRole.FORM_LOGIC, """
                        export class CrmCustomerRulesService { validate(): boolean { return true; } }
                        """),
                source(API_PATH, GitLabFrontendSourceRole.BACKEND_CLIENT, """
                        export class CrmCustomerControllerService {
                          updateCustomer(): void {}
                        }
                        """)
        );
        var totalCharacters = files.stream().mapToInt(GitLabFrontendSourceFile::returnedCharacters).sum();
        return new GitLabFrontendScreenGraphContext(
                scope(), new GitLabFrontendSourceRevision(scope().ref(), "synthetic-crm-revision-20260820"),
                node, chain,
                new GitLabFrontendGraphCoverage(
                        GitLabFrontendCoverageStatus.READY, 1, 1, 20, 8, 0, false, List.of()
                ),
                files, List.of(), List.of(), List.of(), totalCharacters, false
        );
    }

    private GitLabTypeScriptSymbolSliceResponse slice(GitLabTypeScriptSymbolSliceRequest request) {
        List<GitLabTypeScriptDownstreamReference> references = switch (request.filePath()) {
            case ROOT_PATH -> List.of(new GitLabTypeScriptDownstreamReference(
                    GitLabTypeScriptDownstreamReferenceKind.METHOD_CALL,
                    "saveCustomer", "customerFacade", "saveCustomer",
                    "CrmCustomerFacade", "./customer.facade", null
            ));
            case CHILD_PATH -> List.of(
                    new GitLabTypeScriptDownstreamReference(
                            GitLabTypeScriptDownstreamReferenceKind.METHOD_CALL,
                            "submit", "rules", "validate",
                            "CrmCustomerRulesService", "./customer-rules.service", null
                    ),
                    new GitLabTypeScriptDownstreamReference(
                            GitLabTypeScriptDownstreamReferenceKind.PROPERTY_ACCESS,
                            "submit", "customerFacade", "customer$",
                            "CrmCustomerFacade", "./customer.facade", null
                    )
            );
            case FACADE_PATH -> List.of(new GitLabTypeScriptDownstreamReference(
                    GitLabTypeScriptDownstreamReferenceKind.BACKEND_OPERATION,
                    "saveCustomer", "customerApi", "updateCustomer",
                    "CrmCustomerControllerService",
                    "@synthetic-crm/data-access/src/lib/api/services/customer-controller.service", null
            ));
            default -> List.of();
        };
        var entryName = switch (request.filePath()) {
            case ROOT_PATH -> "saveCustomer";
            case CHILD_PATH -> "submit";
            case FACADE_PATH -> "saveCustomer";
            case RULES_PATH -> "validate";
            case API_PATH -> "updateCustomer";
            default -> request.declaringTypeName() != null ? request.declaringTypeName() : "syntheticEntry";
        };
        var candidate = new GitLabTypeScriptSymbolCandidate(
                request.declaringTypeName(), entryName, GitLabTypeScriptSymbolKind.METHOD,
                entryName + "()", 10, 12
        );
        var bindings = Boolean.TRUE.equals(request.includeTemplateBindings())
                ? List.of(new GitLabTypeScriptTemplateBinding(
                        GitLabTypeScriptTemplateBindingKind.EVENT, "click", entryName + "()",
                        List.of(entryName), 1
                ))
                : List.<GitLabTypeScriptTemplateBinding>of();
        var content = "// synthetic CRM slice\n" + entryName + "(): void {}";
        return new GitLabTypeScriptSymbolSliceResponse(
                request.scope(), request.filePath(), "OK", request.declaringTypeName(),
                10, 12, 20, 800, request.templatePath(), bindings.isEmpty() ? 0 : 120, bindings,
                content, content.length(), 800 - content.length(), false,
                List.of(), List.of(), List.of(candidate), List.of(candidate),
                2, 3, 4, references, List.of(candidate), List.of()
        );
    }

    private GitLabFrontendSourceFile source(
            String path,
            GitLabFrontendSourceRole role,
            String content
    ) {
        return new GitLabFrontendSourceFile(path, List.of(role), content, content.length(), false);
    }

    private GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "release/2026.08",
                List.of("apps/synthetic-crm", "libs/synthetic-crm")
        );
    }
}
