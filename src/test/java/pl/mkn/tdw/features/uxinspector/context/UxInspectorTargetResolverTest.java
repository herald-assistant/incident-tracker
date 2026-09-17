package pl.mkn.tdw.features.uxinspector.context;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.frontendcatalog.FrontendApplicationCatalogService;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptTemplateBinding;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptTemplateBindingKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        when(reachability.buildFocused(any(), any())).thenReturn(graph(components));
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
        when(reachability.buildFocused(any(), any())).thenReturn(graph(List.of(
                actionComponent("contact-primary", "primary-action", "doPrimary", 1),
                actionComponent("contact-secondary", "secondary-action", "doSecondary", 2)
        )));
        var resolver = new UxInspectorTargetResolver(applications, reachability);

        var ambiguous = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION,
                capture("cap_crm_ambiguous", "button", "Wykonaj", null));
        var notFound = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION,
                capture("cap_crm_missing", "canvas", "Wykres bez odpowiednika", null));

        assertEquals(UxInspectorTargetResolutionStatus.AMBIGUOUS, ambiguous.status());
        assertNull(ambiguous.sourceBinding());
        assertTrue(ambiguous.focusedSourceSlice().contains("AMBIGUOUS_CANDIDATE 1"));
        assertTrue(ambiguous.focusedSourceSlice().contains("AMBIGUOUS_CANDIDATE 2"));
        assertTrue(ambiguous.focusedSourceSlice().contains("click = doPrimary()"));
        assertTrue(ambiguous.focusedSourceSlice().contains("click = doSecondary()"));
        assertEquals(UxInspectorTargetResolutionStatus.NOT_FOUND, notFound.status());
        assertEquals("", notFound.focusedSourceSlice());
    }

    @Test
    void shouldRankSafeClassSelectorCandidateWithoutDuplicatingStableAttributeEvidence() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var reachability = mock(GitLabFrontendScreenReachabilityService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(reachability.buildFocused(any(), any())).thenReturn(graph(List.of(
                actionComponent("contact-primary", "crm-primary-action", "doPrimary", 1),
                actionComponent("contact-secondary", "crm-secondary-action", "doSecondary", 2)
        )));
        var resolver = new UxInspectorTargetResolver(applications, reachability);
        var selected = withFingerprint(capture("cap_crm_selector", "button", "Wykonaj", null),
                Map.of(), List.of("button[class~=\"crm-primary-action\"]"), List.of());

        var context = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION, selected);

        assertEquals(UxInspectorTargetResolutionStatus.RESOLVED, context.status());
        assertEquals("contact-primary", context.candidates().get(0).componentId());
        assertTrue(context.candidates().get(0).matchReasons().contains("selector class matched"));
        assertEquals(1, context.candidates().get(0).matchReasons().stream()
                .filter(reason -> reason.contains("selector class")).count());
    }

    @Test
    void shouldPreferTheNearestComponentBoundaryAndPreserveItsOrder() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var reachability = mock(GitLabFrontendScreenReachabilityService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(reachability.buildFocused(any(), any())).thenReturn(graph(List.of(
                actionComponent("inner", "inner-action", "inside", 1),
                actionComponent("outer", "outer-action", "outside", 2)
        )));
        var resolver = new UxInspectorTargetResolver(applications, reachability);
        var base = capture("cap_crm_boundary", "div", "Runtime only", null);

        var innerFirst = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION,
                withFingerprint(base, Map.of(), List.of(), List.of("crm-inner", "crm-outer")));
        var outerFirst = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION,
                withFingerprint(base, Map.of(), List.of(), List.of("crm-outer", "crm-inner")));

        assertEquals(UxInspectorTargetResolutionStatus.RESOLVED, innerFirst.status());
        assertEquals("inner", innerFirst.candidates().get(0).componentId());
        assertEquals("outer", outerFirst.candidates().get(0).componentId());
        assertTrue(innerFirst.candidates().get(0).matchReasons().contains(
                "component boundary matched at distance 1"));
    }

    @Test
    void shouldNotUseTheFirstGenericTagWhenNoElementAnchorWasMatched() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var reachability = mock(GitLabFrontendScreenReachabilityService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(reachability.buildFocused(any(), any())).thenReturn(graph(List.of(genericDivComponent())));
        var resolver = new UxInspectorTargetResolver(applications, reachability);
        var selected = withFingerprint(capture("cap_crm_generic", "div", "Runtime only", null),
                Map.of(), List.of(), List.of("crm-container"));

        var context = resolver.resolve("crm-agent-portal", "main", VIEW_ID, REVISION, selected);

        assertEquals(UxInspectorTargetResolutionStatus.RESOLVED, context.status());
        assertNotNull(context.sourceBinding());
        assertEquals("", context.sourceBinding().elementSnippet());
        assertTrue(context.sourceBinding().elementBindings().isEmpty());
        assertFalse(context.sourceBinding().sourceReference().contains("#L"));
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
        when(reachability.buildFocused(any(), any())).thenReturn(graph(List.of(component)));
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
        when(reachability.buildFocused(any(), any())).thenThrow(new GitLabFrontendDiscoveryException(
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
        when(reachability.buildFocused(any(), any())).thenReturn(graph(List.of(
                component("contact-create", "contact-save", "Zapisz kontakt", 1))));
        var resolver = new UxInspectorTargetResolver(applications, reachability);

        var error = assertThrows(UxInspectorContextException.class, () -> resolver.resolve(
                "crm-agent-portal", "main", VIEW_ID, "different-revision", capture()));

        assertEquals("UX_INSPECTOR_SOURCE_REVISION_CHANGED", error.code());
        assertTrue(error.getMessage().contains("Reload views"));
    }

    private GitLabFrontendReachabilityComponent actionComponent(
            String id,
            String className,
            String handler,
            int order
    ) {
        var symbol = "Crm" + id.replaceAll("[^A-Za-z0-9]", "") + "Component";
        var templatePath = "src/app/contacts/" + id + ".component.html";
        var sourcePath = "src/app/contacts/" + id + ".component.ts";
        var template = "<button class=\"" + className + "\" (click)=\"" + handler
                + "()\">Wykonaj</button>";
        return new GitLabFrontendReachabilityComponent(id, order, 0, true, "ROUTE_TARGET", symbol,
                "crm-" + id, sourcePath, templatePath, template, "RESOLVED",
                List.of(new GitLabTypeScriptTemplateBinding(GitLabTypeScriptTemplateBindingKind.EVENT,
                        "click", handler + "()", List.of(handler), 1)),
                List.of(), List.of(), List.of(), List.of(),
                "export class " + symbol + " { " + handler + "() {} }", 120, 120, false, List.of());
    }

    private GitLabFrontendReachabilityComponent genericDivComponent() {
        return new GitLabFrontendReachabilityComponent("container", 1, 0, true, "ROUTE_TARGET",
                "CrmContainerComponent", "crm-container", "src/app/contacts/container.component.ts",
                "src/app/contacts/container.component.html",
                "<div (click)=\"wrongTarget()\">Pierwszy</div>\n<div>Drugi</div>", "RESOLVED",
                List.of(new GitLabTypeScriptTemplateBinding(GitLabTypeScriptTemplateBindingKind.EVENT,
                        "click", "wrongTarget()", List.of("wrongTarget"), 1)),
                List.of(), List.of(), List.of(), List.of(),
                "export class CrmContainerComponent { wrongTarget() {} }", 120, 120, false, List.of());
    }

    private UxInspectorCapture withFingerprint(
            UxInspectorCapture capture,
            Map<String, String> stableAttributes,
            List<String> selectorCandidates,
            List<String> componentBoundaryTags
    ) {
        var original = capture.target();
        var fingerprint = new UxInspectorCapture.DomFingerprint(stableAttributes, selectorCandidates,
                componentBoundaryTags, original.domFingerprint().labelFor());
        var target = new UxInspectorCapture.Target(original.tag(), original.role(), original.accessibleName(),
                original.text(), fingerprint, original.state(), original.bounds());
        return new UxInspectorCapture(capture.schema(), capture.version(), capture.captureId(), capture.capturedAt(),
                capture.captureProfile(), capture.page(), target, capture.ancestors(), capture.formSnapshot(),
                capture.traversal(), capture.signals(), capture.limits(), capture.client());
    }
}
