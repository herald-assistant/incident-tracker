package pl.mkn.tdw.features.uxinspector.context;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.frontendcatalog.FrontendApplicationCatalogService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorTargetResolverTest {

    @Test
    void shouldResolveAtLeastEightyPercentOfTwelveFocusedCrmFixtures() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var reachability = mock(GitLabFrontendScreenReachabilityService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        var components = new ArrayList<pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent>();
        var fixtureKinds = List.of("validation", "validation", "validation", "data-origin", "data-origin", "data-origin",
                "state", "state", "state", "action", "action", "action");
        for (var index = 0; index < fixtureKinds.size(); index++) {
            components.add(component(fixtureKinds.get(index) + "-" + index,
                    "crm-target-" + index, "CRM target " + index, index + 1));
        }
        when(reachability.build(any())).thenReturn(graph(components));
        var resolver = new UxInspectorTargetResolver(applications, reachability);

        var correct = 0;
        for (var index = 0; index < components.size(); index++) {
            var context = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION,
                    capture("cap_crm_fixture_" + index, "button", "CRM target " + index, "crm-target-" + index));
            if (context.status() == UxInspectorTargetResolutionStatus.RESOLVED
                    && context.candidates().get(0).componentId().equals(components.get(index).componentId())) {
                correct++;
            }
            assertTrue(context.candidates().get(0).matchReasons().stream()
                    .anyMatch(reason -> reason.contains("data-testid")));
            assertTrue(context.focusedSourceSlice().contains(components.get(index).templatePath()));
            assertNotNull(context.sourceBinding());
            assertEquals("EXTERNAL", context.sourceBinding().templateKind());
            assertTrue(context.sourceBinding().sourceReference().startsWith(components.get(index).templatePath() + "#L"));
        }

        assertTrue(correct >= 10, "Expected >=80% correct target resolution, got " + correct + "/12");
    }

    @Test
    void shouldPreserveAmbiguityAndNotFoundInsteadOfGuessing() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var reachability = mock(GitLabFrontendScreenReachabilityService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(reachability.build(any())).thenReturn(graph(List.of(
                component("contact-primary", "primary-action", "Wykonaj", 1),
                component("contact-secondary", "secondary-action", "Wykonaj", 2)
        )));
        var resolver = new UxInspectorTargetResolver(applications, reachability);

        var ambiguous = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION,
                capture("cap_crm_ambiguous", "button", "Wykonaj", null));
        var notFound = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION,
                capture("cap_crm_missing", "canvas", "Wykres bez odpowiednika", null));

        assertEquals(UxInspectorTargetResolutionStatus.AMBIGUOUS, ambiguous.status());
        assertEquals("", ambiguous.focusedSourceSlice());
        assertEquals(UxInspectorTargetResolutionStatus.NOT_FOUND, notFound.status());
        assertEquals("", notFound.focusedSourceSlice());
    }

    @Test
    void shouldIncludeInlineAngularTemplateInFocusedEvidence() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var reachability = mock(GitLabFrontendScreenReachabilityService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        var sourcePath = "src/app/login/login.component.ts";
        var template = """
                <form (ngSubmit)="doLogin()">
                  <input data-testid="email" required [(ngModel)]="email">
                </form>
                """;
        var component = new GitLabFrontendReachabilityComponent(
                "login", 1, 0, true, "ROUTE_TARGET", "LoginComponent", "app-login",
                sourcePath, null, template,
                "RESOLVED", List.of(
                        new pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptTemplateBinding(
                                pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptTemplateBindingKind.EVENT,
                                "ngSubmit", "doLogin()", List.of("doLogin"), 1),
                        new pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptTemplateBinding(
                                pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptTemplateBindingKind.TWO_WAY,
                                "ngModel", "email", List.of("email"), 2)
                ), List.of(), List.of(), List.of(), List.of(),
                "export class LoginComponent { email = ''; doLogin() {} }", 120, 120, false, List.of()
        );
        when(reachability.build(any())).thenReturn(graph(List.of(component)));
        var resolver = new UxInspectorTargetResolver(applications, reachability);

        var context = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION,
                capture("cap_crm_email", "input", "Email", "email"));

        assertEquals(UxInspectorTargetResolutionStatus.RESOLVED, context.status());
        assertTrue(context.focusedSourceSlice().contains("INLINE_TEMPLATE FROM " + sourcePath));
        assertTrue(context.focusedSourceSlice().contains("required [(ngModel)]=\"email\""));
        assertTrue(context.focusedSourceSlice().contains("export class LoginComponent"));
        assertNotNull(context.sourceBinding());
        assertEquals("INLINE", context.sourceBinding().templateKind());
        assertEquals(sourcePath, context.sourceBinding().sourceReference());
        assertEquals("email", context.sourceBinding().elementBindings().get(0).expression());
        assertEquals("doLogin()", context.sourceBinding().formSubmitBinding().expression());
        assertFalse(context.sourceBinding().sourceReference().contains("#L"));
    }

    @Test
    void shouldMapStaleRevisionToConflictWithoutTryingAnotherRevision() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var reachability = mock(GitLabFrontendScreenReachabilityService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(reachability.build(any())).thenThrow(new GitLabFrontendDiscoveryException(
                "FRONTEND_SOURCE_REVISION_CHANGED", "revision changed"));
        var resolver = new UxInspectorTargetResolver(applications, reachability);

        var error = assertThrows(UxInspectorContextException.class, () -> resolver.resolve(
                "crm-agent-portal", "main", VIEW_ID, REVISION, capture()));

        assertEquals("UX_INSPECTOR_SOURCE_REVISION_CHANGED", error.code());
        assertTrue(error.getMessage().contains("Reload views"));
    }

    @Test
    void shouldDefensivelyRejectGraphBuiltFromAnotherRevision() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var reachability = mock(GitLabFrontendScreenReachabilityService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(reachability.build(any())).thenReturn(graph(List.of(
                component("contact-create", "contact-save", "Zapisz kontakt", 1))));
        var resolver = new UxInspectorTargetResolver(applications, reachability);

        var error = assertThrows(UxInspectorContextException.class, () -> resolver.resolve(
                "crm-agent-portal", "main", VIEW_ID, "different-revision", capture()));

        assertEquals("UX_INSPECTOR_SOURCE_REVISION_CHANGED", error.code());
        assertTrue(error.getMessage().contains("Reload views"));
    }
}
