package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitLabFrontendSourceDiscoveryServiceTest {

    @Test
    void shouldDiscoverStandaloneAndModuleBasedCrmScreensWithRevisionAndWorkspaceSignals() {
        var service = service();

        var catalog = service.discoverCatalog(catalogRequest(defaultLimits()));

        assertThat(catalog.sourceRevision())
                .isEqualTo(new GitLabFrontendSourceRevision("main", "crm-commit-abc123"));
        assertThat(catalog.workspaceSignals())
                .extracting(GitLabFrontendWorkspaceSignal::value)
                .contains("ANGULAR", "ANGULAR_CLI", "NX", "NGRX", "RXJS", "KEYCLOAK", "ANGULAR_MATERIAL");
        assertThat(catalog.entries()).filteredOn(entry -> entry.kind() == GitLabFrontendRouteEntryKind.SCREEN)
                .hasSize(3);
        assertThat(catalog.entries()).anySatisfy(entry -> {
            if (!"/contacts/:contactId/preferences".equals(entry.routePattern())) {
                return;
            }
            assertThat(entry.kind()).isEqualTo(GitLabFrontendRouteEntryKind.SCREEN);
            assertThat(entry.status()).isEqualTo(GitLabFrontendDiscoveryStatus.RESOLVED);
            assertThat(entry.label()).isEqualTo("Preferences");
            assertThat(entry.parentRoutePattern()).isEqualTo("/contacts/:contactId");
            assertThat(entry.lazyLoaded()).isTrue();
            assertThat(entry.guards()).containsExactly("CrmAuthGuard");
            assertThat(entry.routeParameters()).containsExactly("contactId");
            assertThat(entry.viewSourcePath()).endsWith("crm-contact-preferences.component.ts");
            assertThat(entry.screenId()).startsWith("screen-");
        });
        assertThat(catalog.entries()).anySatisfy(entry -> {
            if (!"/segments".equals(entry.routePattern())) {
                return;
            }
            assertThat(entry.kind()).isEqualTo(GitLabFrontendRouteEntryKind.SCREEN);
            assertThat(entry.lazyLoaded()).isTrue();
            assertThat(entry.guards()).contains("CrmRoleGuard");
            assertThat(entry.viewSymbol()).isEqualTo("CrmSegmentComparisonComponent");
        });
        assertThat(catalog.entries()).anySatisfy(entry -> {
            if (!"/legacy".equals(entry.routePattern())) {
                return;
            }
            assertThat(entry.kind()).isEqualTo(GitLabFrontendRouteEntryKind.REDIRECT);
            assertThat(entry.redirectTarget()).isEqualTo("contacts");
        });
        assertThat(catalog.entries()).anySatisfy(entry -> {
            if (!"/runtime-view".equals(entry.routePattern())) {
                return;
            }
            assertThat(entry.kind()).isEqualTo(GitLabFrontendRouteEntryKind.UNRESOLVED);
            assertThat(entry.status()).isEqualTo(GitLabFrontendDiscoveryStatus.PARTIAL);
        });
        assertThat(catalog.entries()).anySatisfy(entry -> {
            if (!"/runtime-area".equals(entry.routePattern())) {
                return;
            }
            assertThat(entry.kind()).isEqualTo(GitLabFrontendRouteEntryKind.UNRESOLVED);
            assertThat(entry.status()).isEqualTo(GitLabFrontendDiscoveryStatus.UNSUPPORTED);
        });
        assertThat(catalog.diagnostics())
                .extracting(GitLabFrontendDiagnostic::code)
                .contains(
                        "ANGULAR_ROUTE_DEFINITION_PARTIAL",
                        "HEURISTIC_TYPESCRIPT_DISCOVERY",
                        "LAZY_ROUTE_SOURCE_UNRESOLVED"
                );
        assertThat(catalog.inventoryTruncated()).isFalse();
        assertThat(catalog.routeCatalogTruncated()).isFalse();
    }

    @Test
    void shouldBuildBoundedCrmScreenContextWithFormsStateRestWebSocketAndAuthSignals() {
        var service = service();
        var catalog = service.discoverCatalog(catalogRequest(defaultLimits()));
        var screen = catalog.entries().stream()
                .filter(entry -> "/contacts/:contactId/preferences".equals(entry.routePattern()))
                .findFirst()
                .orElseThrow();

        var context = service.buildScreenContext(new GitLabFrontendScreenContextRequest(
                scope(),
                screen.screenId(),
                defaultLimits()
        ));

        assertThat(context.screen()).isEqualTo(screen);
        assertThat(context.sourceFiles())
                .extracting(GitLabFrontendSourceFile::path)
                .contains(
                        "apps/crm-agent/src/app/app.routes.ts",
                        "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts",
                        "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.html",
                        "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.scss",
                        "apps/crm-agent/src/app/contact-preferences/controls/crm-channel-control.component.ts",
                        "apps/crm-agent/src/app/contact-preferences/state/crm-contact.actions.ts",
                        "apps/crm-agent/src/app/contact-preferences/state/crm-contact.selectors.ts",
                        "apps/crm-agent/src/app/contact-preferences/state/crm-contact.effects.ts",
                        "apps/crm-agent/src/app/contact-preferences/state/crm-contact.reducer.ts",
                        "apps/crm-agent/src/app/contact-preferences/services/crm-contact.api.ts",
                        "apps/crm-agent/src/app/contact-preferences/services/crm-contact-http-fallback.ts",
                        "apps/crm-agent/src/app/contact-preferences/services/crm-contact.socket.ts"
                );
        assertThat(context.technicalSignals())
                .extracting(GitLabFrontendTechnicalSignal::kind)
                .contains(
                        GitLabFrontendTechnicalSignalKind.REACTIVE_FORM,
                        GitLabFrontendTechnicalSignalKind.CUSTOM_FORM_CONTROL,
                        GitLabFrontendTechnicalSignalKind.DYNAMIC_FORM_DEFINITION,
                        GitLabFrontendTechnicalSignalKind.NGRX_STORE,
                        GitLabFrontendTechnicalSignalKind.NGRX_ACTION,
                        GitLabFrontendTechnicalSignalKind.NGRX_SELECTOR,
                        GitLabFrontendTechnicalSignalKind.NGRX_EFFECT,
                        GitLabFrontendTechnicalSignalKind.NGRX_REDUCER,
                        GitLabFrontendTechnicalSignalKind.REST_CLIENT,
                        GitLabFrontendTechnicalSignalKind.HTTP_CLIENT,
                        GitLabFrontendTechnicalSignalKind.WEBSOCKET,
                        GitLabFrontendTechnicalSignalKind.RXJS_STREAM,
                        GitLabFrontendTechnicalSignalKind.AUTH_GUARD,
                        GitLabFrontendTechnicalSignalKind.ROLE_OR_PERMISSION_CHECK
                );
        assertThat(context.coverage())
                .filteredOn(coverage -> coverage.category().equals("FORMS"))
                .singleElement()
                .extracting(GitLabFrontendContextCoverage::status)
                .isEqualTo(GitLabFrontendCoverageStatus.READY);
        assertThat(context.coverage())
                .filteredOn(coverage -> coverage.category().equals("BACKEND_SERVICES"))
                .singleElement()
                .extracting(GitLabFrontendContextCoverage::status)
                .isEqualTo(GitLabFrontendCoverageStatus.READY);
        assertThat(context.totalReturnedCharacters()).isPositive();
        assertThat(context.repositoryFileCount()).isPositive();
        assertThat(context.scannedRouteFileCount()).isPositive();
        assertThat(context.truncated()).isFalse();
    }

    @Test
    void shouldRejectCrmContextWhenCatalogRevisionChangedBeforeScreenValidation() {
        var service = service();

        assertThatThrownBy(() -> service.buildScreenContext(new GitLabFrontendScreenContextRequest(
                scope(),
                "screen-crm-stale-selection",
                "crm-commit-previous",
                defaultLimits()
        )))
                .isInstanceOf(GitLabFrontendDiscoveryException.class)
                .extracting(exception -> ((GitLabFrontendDiscoveryException) exception).code())
                .isEqualTo("FRONTEND_SOURCE_REVISION_CHANGED");
    }

    @Test
    void shouldRecognizeInlineTemplateAndStyleWithoutInventingExternalFiles() {
        var service = service();
        var catalog = service.discoverCatalog(catalogRequest(defaultLimits()));
        var dashboard = catalog.entries().stream()
                .filter(entry -> "/dashboard".equals(entry.routePattern()))
                .findFirst()
                .orElseThrow();

        var context = service.buildScreenContext(new GitLabFrontendScreenContextRequest(
                scope(), dashboard.screenId(), defaultLimits()
        ));

        assertThat(context.sourceFiles())
                .filteredOn(file -> file.path().endsWith("crm-dashboard.component.ts"))
                .singleElement()
                .satisfies(file -> assertThat(file.roles())
                        .contains(
                                GitLabFrontendSourceRole.VIEW_COMPONENT,
                                GitLabFrontendSourceRole.INLINE_TEMPLATE,
                                GitLabFrontendSourceRole.INLINE_STYLE
                        ));
        assertThat(context.sourceFiles())
                .noneMatch(file -> file.path().endsWith("crm-dashboard.component.html"));
    }

    @Test
    void shouldKeepScreenIdStableAndRejectUnknownSelection() {
        var service = service();
        var first = service.discoverCatalog(catalogRequest(defaultLimits()));
        var second = service.discoverCatalog(catalogRequest(defaultLimits()));
        assertThat(second.entries())
                .extracting(GitLabFrontendRouteEntry::screenId)
                .containsExactlyElementsOf(first.entries().stream().map(GitLabFrontendRouteEntry::screenId).toList());

        assertThatThrownBy(() -> service.buildScreenContext(new GitLabFrontendScreenContextRequest(
                scope(), "screen-crm-unknown", defaultLimits()
        )))
                .isInstanceOf(GitLabFrontendDiscoveryException.class)
                .extracting(exception -> ((GitLabFrontendDiscoveryException) exception).code())
                .isEqualTo("FRONTEND_SCREEN_NOT_FOUND");
    }

    @Test
    void shouldExposeInventoryAndContextLimitsAsDiagnostics() {
        var service = service();
        var inventoryLimited = new GitLabFrontendDiscoveryLimits(1, 10, 20, 10, 20_000, 100_000, 2);

        var catalog = service.discoverCatalog(catalogRequest(inventoryLimited));

        assertThat(catalog.inventoryTruncated()).isTrue();
        assertThat(catalog.entries()).isEmpty();
        assertThat(catalog.diagnostics())
                .extracting(GitLabFrontendDiagnostic::code)
                .contains("REPOSITORY_INVENTORY_LIMIT_REACHED", "ANGULAR_ROUTE_SOURCE_NOT_FOUND");

        var complete = service.discoverCatalog(catalogRequest(defaultLimits()));
        var screen = complete.entries().stream()
                .filter(entry -> "/contacts/:contactId/preferences".equals(entry.routePattern()))
                .findFirst()
                .orElseThrow();
        var contextLimited = new GitLabFrontendDiscoveryLimits(2_000, 80, 400, 3, 50_000, 3_000, 3);
        var context = service.buildScreenContext(new GitLabFrontendScreenContextRequest(
                scope(), screen.screenId(), contextLimited
        ));

        assertThat(context.truncated()).isTrue();
        assertThat(context.sourceFiles()).hasSizeLessThanOrEqualTo(3);
        assertThat(context.diagnostics())
                .extracting(GitLabFrontendDiagnostic::code)
                .containsAnyOf("SOURCE_FILE_LIMIT_REACHED", "SCREEN_CONTEXT_CONTENT_LIMIT_REACHED");
    }

    @Test
    void shouldFailExplicitlyWhenCrmBranchDoesNotExist() {
        var service = new GitLabFrontendSourceDiscoveryService(
                new CrmFrontendGitLabRepositoryPort(Map.of(), false)
        );

        assertThatThrownBy(() -> service.discoverCatalog(catalogRequest(defaultLimits())))
                .isInstanceOf(GitLabFrontendDiscoveryException.class)
                .extracting(exception -> ((GitLabFrontendDiscoveryException) exception).code())
                .isEqualTo("FRONTEND_REF_NOT_FOUND");
    }

    @Test
    void shouldRespectCrmRepositoryPathPrefix() {
        var service = service();
        var scoped = new GitLabFrontendRepositoryScope(
                "crm",
                "agent-portal",
                "main",
                java.util.List.of("apps/crm-agent")
        );

        var catalog = service.discoverCatalog(new GitLabFrontendRouteCatalogRequest(scoped, defaultLimits()));

        assertThat(catalog.entries()).anyMatch(entry -> "/dashboard".equals(entry.routePattern()));
        assertThat(catalog.workspaceSignals())
                .extracting(GitLabFrontendWorkspaceSignal::value)
                .contains("NX_PROJECT")
                .doesNotContain("ANGULAR_MATERIAL", "KEYCLOAK");
    }

    @Test
    void shouldRejectAttemptsToDisableHardDiscoveryBounds() {
        assertThatThrownBy(() -> new GitLabFrontendDiscoveryLimits(
                10_001,
                80,
                400,
                40,
                50_000,
                500_000,
                3
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxInventoryFiles");
    }

    @Test
    void shouldResolveCrossFileCrmRouteModelsAndNxAliasesWithoutExecutingTypeScript() {
        var service = new GitLabFrontendSourceDiscoveryService(
                new CrmFrontendGitLabRepositoryPort(aliasedCrmRouteFiles(), true)
        );

        var catalog = service.discoverCatalog(catalogRequest(defaultLimits()));

        assertThat(catalog.entries()).anySatisfy(entry -> {
            if (!"/contacts/active/details".equals(entry.routePattern())) {
                return;
            }
            assertThat(entry.kind()).isEqualTo(GitLabFrontendRouteEntryKind.SCREEN);
            assertThat(entry.status()).isEqualTo(GitLabFrontendDiscoveryStatus.RESOLVED);
            assertThat(entry.parentRoutePattern()).isEqualTo("/contacts");
            assertThat(entry.lazyLoaded()).isTrue();
            assertThat(entry.guards()).containsExactly("CrmAccessGuard", "CrmDetailsGuard");
            assertThat(entry.viewSymbol()).isEqualTo("CrmContactDetailsComponent");
            assertThat(entry.viewSourcePath())
                    .isEqualTo("libs/crm-features/contact-area/crm-contact-details.component.ts");
        });
        assertThat(catalog.entries()).anySatisfy(entry -> {
            if (!"/legacy-contact".equals(entry.routePattern())) {
                return;
            }
            assertThat(entry.kind()).isEqualTo(GitLabFrontendRouteEntryKind.REDIRECT);
            assertThat(entry.redirectTarget()).isEqualTo("active/details");
        });
        assertThat(catalog.diagnostics())
                .noneMatch(diagnostic -> diagnostic.code().equals("LAZY_ROUTE_SOURCE_UNRESOLVED"));

        var screen = catalog.entries().stream()
                .filter(entry -> "/contacts/active/details".equals(entry.routePattern()))
                .findFirst()
                .orElseThrow();
        var context = service.buildScreenContext(new GitLabFrontendScreenContextRequest(
                scope(), screen.screenId(), defaultLimits()
        ));
        assertThat(context.sourceFiles())
                .extracting(GitLabFrontendSourceFile::path)
                .contains(
                        "libs/crm-features/contact-area/crm-contact-area.routes.ts",
                        "libs/crm-features/contact-area/crm-contact-details.component.ts"
                );
    }

    private static Map<String, String> aliasedCrmRouteFiles() {
        var files = new java.util.LinkedHashMap<String, String>();
        files.put("package.json", "{ \"name\": \"crm-agent-portal\" }");
        files.put("tsconfig.base.json", """
                {
                  // Strongly anonymized CRM aliases.
                  "compilerOptions": {
                    "baseUrl": ".",
                    "paths": {
                      "@crm/routing/*": ["libs/crm-routing/src/lib/*"],
                      "@crm/features/*": ["libs/crm-features/*"]
                    },
                  },
                }
                """);
        files.put("apps/crm-agent/src/app/app.routes.ts", """
                import { Routes } from '@angular/router';
                import { CRM_ROUTE_MODEL } from '@crm/routing/crm-route-model';

                export const CRM_APP_ROUTES: Routes = [
                  {
                    path: CRM_ROUTE_MODEL.contacts.path,
                    canActivate: [CrmAccessGuard],
                    loadChildren: () => import('@crm/features/contact-area/crm-contact-area.routes')
                      .then(module => module.CRM_CONTACT_AREA_ROUTES)
                  },
                  {
                    path: 'legacy-contact',
                    redirectTo: CRM_ROUTE_MODEL.contacts.details.path
                  }
                ];
                """);
        files.put("libs/crm-routing/src/lib/crm-stage.ts", """
                export enum CRM_STAGE {
                  ACTIVE = 'active'
                }
                """);
        files.put("libs/crm-routing/src/lib/crm-route-model.ts", """
                import { CRM_STAGE } from '@crm/routing/crm-stage';

                export const CRM_ROUTE_MODEL: CrmRouteModel = {
                  path: '',
                  contacts: {
                    path: 'contacts',
                    details: {
                      path: `${CRM_STAGE.ACTIVE}/details`
                    }
                  }
                };
                """);
        files.put("libs/crm-features/contact-area/crm-contact-area.routes.ts", """
                import { Routes } from '@angular/router';
                import { CRM_ROUTE_MODEL } from '@crm/routing/crm-route-model';
                import { CrmContactDetailsComponent } from './crm-contact-details.component';

                export const CRM_CONTACT_AREA_ROUTES: Routes = [
                  {
                    path: CRM_ROUTE_MODEL.path,
                    children: [
                      {
                        path: CRM_ROUTE_MODEL.contacts.details.path,
                        component: CrmContactDetailsComponent,
                        canActivate: [CrmDetailsGuard]
                      }
                    ]
                  }
                ];
                """);
        files.put("libs/crm-features/contact-area/crm-contact-details.component.ts", """
                @Component({ template: '<p>Synthetic CRM contact details</p>' })
                export class CrmContactDetailsComponent {}
                """);
        return Map.copyOf(files);
    }

    private static GitLabFrontendSourceDiscoveryService service() {
        return new GitLabFrontendSourceDiscoveryService(new CrmFrontendGitLabRepositoryPort());
    }

    private static GitLabFrontendRouteCatalogRequest catalogRequest(GitLabFrontendDiscoveryLimits limits) {
        return new GitLabFrontendRouteCatalogRequest(scope(), limits);
    }

    private static GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope("crm", "agent-portal", "main", java.util.List.of());
    }

    private static GitLabFrontendDiscoveryLimits defaultLimits() {
        return GitLabFrontendDiscoveryLimits.defaults();
    }
}
