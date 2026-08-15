package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeScriptStaticRouteResolverTest {

    @Test
    void shouldRejectAmbiguousCrmAliasWithoutChoosingAnArbitraryRouteModel() {
        var files = new LinkedHashMap<String, String>();
        files.put("tsconfig.base.json", """
                {
                  "compilerOptions": {
                    "paths": {
                      "@crm/routes": ["libs/crm-a/routes", "libs/crm-b/routes"]
                    }
                  }
                }
                """);
        files.put("apps/crm/app.routes.ts", """
                import { CRM_ROUTES } from '@crm/routes';
                export const routes: Routes = [{ path: CRM_ROUTES.contacts.path }];
                """);
        files.put("libs/crm-a/routes.ts", routeModel("contacts-a"));
        files.put("libs/crm-b/routes.ts", routeModel("contacts-b"));
        var resolver = resolver(files);

        var result = resolver.resolve(
                "apps/crm/app.routes.ts",
                files.get("apps/crm/app.routes.ts"),
                "CRM_ROUTES.contacts.path"
        );

        assertThat(result.value()).isNull();
        assertThat(result.limitation()).contains("ambiguous");
    }

    @Test
    void shouldStopAStaticCrmRouteModelCycle() {
        var files = Map.of(
                "apps/crm/app.routes.ts", """
                        const CRM_ROUTES = { contacts: { path: CRM_ROUTES.contacts.path } };
                        """
        );
        var resolver = resolver(files);

        var result = resolver.resolve(
                "apps/crm/app.routes.ts",
                files.get("apps/crm/app.routes.ts"),
                "CRM_ROUTES.contacts.path"
        );

        assertThat(result.value()).isNull();
        assertThat(result.limitation()).contains("cycle");
    }

    private static TypeScriptStaticRouteResolver resolver(Map<String, String> files) {
        return new TypeScriptStaticRouteResolver(files.keySet().stream().sorted().toList(), files::get);
    }

    private static String routeModel(String path) {
        return """
                export const CRM_ROUTES = {
                  contacts: { path: '%s' }
                };
                """.formatted(path);
    }
}
