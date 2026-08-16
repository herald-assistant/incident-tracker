package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AngularRouteSourceParserTest {

    private final AngularRouteSourceParser parser = new AngularRouteSourceParser();

    @Test
    void shouldParseStandaloneNestedLazyRoutesGuardsRedirectsAndParameters() {
        var result = parser.parse("apps/crm-agent/src/app/app.routes.ts", """
                export const appRoutes: Routes = [
                  {
                    path: 'contacts/:contactId',
                    canActivate: [CrmAuthGuard],
                    children: [
                      {
                        path: 'preferences',
                        loadComponent: () => import('./contact-preferences/crm-contact-preferences.component')
                          .then(module => module.CrmContactPreferencesComponent)
                      }
                    ]
                  },
                  {
                    path: 'segments',
                    canMatch: [CrmRoleGuard],
                    loadChildren: () => import('./segments/crm-segments.module')
                      .then(module => module.CrmSegmentsModule)
                  },
                  { path: 'legacy', redirectTo: 'contacts' }
                ];
                """);

        assertThat(result.routes()).anySatisfy(route -> {
            assertThat(route.fullPath()).isEqualTo("/contacts/:contactId/preferences");
            assertThat(route.loadComponentImportPath())
                    .isEqualTo("./contact-preferences/crm-contact-preferences.component");
            assertThat(route.loadComponentSymbol()).isEqualTo("CrmContactPreferencesComponent");
            assertThat(route.guards()).containsExactly("CrmAuthGuard");
            assertThat(route.lazy()).isTrue();
        });
        assertThat(result.routes()).anySatisfy(route -> {
            assertThat(route.fullPath()).isEqualTo("/segments");
            assertThat(route.loadChildrenSymbol()).isEqualTo("CrmSegmentsModule");
            assertThat(route.guards()).containsExactly("CrmRoleGuard");
        });
        assertThat(result.routes()).anySatisfy(route -> {
            assertThat(route.fullPath()).isEqualTo("/legacy");
            assertThat(route.redirectTo()).isEqualTo("contacts");
        });
    }

    @Test
    void shouldParseModuleBasedForChildRoutes() {
        var result = parser.parse("apps/crm-agent/src/app/segments/crm-segments-routing.module.ts", """
                const routes: Routes = [
                  { path: '', component: CrmSegmentComparisonComponent }
                ];

                @NgModule({ imports: [RouterModule.forChild(routes)] })
                export class CrmSegmentsRoutingModule {}
                """);

        assertThat(result.routes()).singleElement().satisfies(route -> {
            assertThat(route.fullPath()).isEqualTo("/");
            assertThat(route.componentSymbol()).isEqualTo("CrmSegmentComparisonComponent");
        });
    }

    @Test
    void shouldReportRuntimeSpreadInsteadOfExpandingIt() {
        var result = parser.parse("apps/crm-agent/src/app/app.routes.ts", """
                export const appRoutes: Routes = [
                  { path: 'contacts', component: CrmContactsComponent },
                  ...crmRuntimeRoutes
                ];
                """);

        assertThat(result.limitations())
                .contains("Spread route definitions are runtime-dependent and were not expanded in "
                        + "apps/crm-agent/src/app/app.routes.ts.");
    }

    @Test
    void shouldPreserveDynamicLazyDeclarationsAsUnresolvedParserSignals() {
        var result = parser.parse("apps/crm-agent/src/app/app.routes.ts", """
                export const appRoutes: Routes = [
                  { path: 'runtime-view', loadComponent: crmRuntimeViewFactory },
                  { path: 'runtime-area', loadChildren: crmRuntimeRoutesFactory },
                  { path: 'computed-view', loadComponent: () => import('./computed-view.component')
                      .then(module => module[crmRuntimeComponentName]) }
                ];
                """);

        assertThat(result.routes()).anySatisfy(route -> {
            if (!"/runtime-view".equals(route.fullPath())) {
                return;
            }
            assertThat(route.loadComponentDeclared()).isTrue();
            assertThat(route.loadComponentImportPath()).isNull();
        });
        assertThat(result.routes()).anySatisfy(route -> {
            if (!"/runtime-area".equals(route.fullPath())) {
                return;
            }
            assertThat(route.loadChildrenDeclared()).isTrue();
            assertThat(route.loadChildrenImportPath()).isNull();
        });
        assertThat(result.routes()).anySatisfy(route -> {
            if (!"/computed-view".equals(route.fullPath())) {
                return;
            }
            assertThat(route.loadComponentImportPath()).isEqualTo("./computed-view.component");
            assertThat(route.loadComponentSymbol()).isNull();
        });
    }

    @Test
    void shouldTreatDirectCrmDynamicImportAsDefaultExport() {
        var result = parser.parse("apps/crm-agent/src/app/app.routes.ts", """
                export const appRoutes: Routes = [
                  {
                    path: 'contact-summary',
                    loadComponent: () => import('./contact-summary/crm-contact-summary.component')
                  }
                ];
                """);

        assertThat(result.routes()).singleElement().satisfies(route -> {
            assertThat(route.loadComponentImportPath())
                    .isEqualTo("./contact-summary/crm-contact-summary.component");
            assertThat(route.loadComponentSymbol()).isEqualTo("default");
        });
    }

    @Test
    void shouldRecognizeCrmLocalLazyFactoryAndFlattenedStaticRouteConfig() {
        var result = parser.parse("apps/crm-agent/src/app/crm.routes.ts", """
                export const CRM_ROUTES: Routes = [
                  {
                    path: 'contacts',
                    loadComponent: () => CrmContactListComponent
                  },
                  {
                    path: 'workflow',
                    children: CRM_WORKFLOW_CONFIG.reduce<Routes>(
                      (acc, current) => [...acc, ...current.routes],
                      []
                    )
                  }
                ];
                """);

        assertThat(result.routes()).anySatisfy(route -> {
            if (!"/contacts".equals(route.fullPath())) {
                return;
            }
            assertThat(route.loadComponentImportPath()).isNull();
            assertThat(route.loadComponentSymbol()).isEqualTo("CrmContactListComponent");
        });
        assertThat(result.routes()).anySatisfy(route -> {
            if (!"/workflow".equals(route.fullPath())) {
                return;
            }
            assertThat(route.childrenSymbol()).isEqualTo("CRM_WORKFLOW_CONFIG");
        });
        assertThat(result.limitations())
                .noneMatch(limitation -> limitation.contains("Dynamic children route definition"));
    }
}
