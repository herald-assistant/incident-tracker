package pl.mkn.tdw.features.uxinspector;

import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.features.uxinspector.context.*;
import pl.mkn.tdw.frontendcatalog.FrontendApplicationCatalog;
import pl.mkn.tdw.frontendcatalog.FrontendApplicationRegistration;
import pl.mkn.tdw.integrations.gitlab.frontend.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UxInspectorTestFixtures {
    public static final String REVISION = "1234567890abcdef1234567890abcdef12345678";
    public static final String VIEW_ID = "crm-contact-create";
    public static final String TEMPLATE_PATH = "src/app/contacts/contact-create.component.html";
    public static final String SOURCE_PATH = "src/app/contacts/contact-create.component.ts";

    private UxInspectorTestFixtures() {}

    public static UxInspectorCapture capture() {
        return capture("cap_crm_contact_save", "button", "Zapisz kontakt", "contact-save");
    }

    public static UxInspectorCapture capture(String captureId, String tag, String label, String testId) {
        return new UxInspectorCapture(UxInspectorCapture.SCHEMA, UxInspectorCapture.VERSION, captureId,
                Instant.parse("2026-09-15T10:00:00Z"),
                UxInspectorCapture.CaptureProfile.ELEMENT_CONTEXT,
                new UxInspectorCapture.Page("https://crm.example.com", "/contacts/new", "CRM Agent Portal", "pl", List.of("view")),
                new UxInspectorCapture.Target(tag, "button", label, label,
                        new UxInspectorCapture.DomFingerprint(
                                testId != null ? Map.of("data-testid", testId) : Map.of(),
                                testId != null ? List.of(tag + "[data-testid=\"" + testId + "\"]") : List.of(),
                                List.of("crm-contact-form"), null),
                        new UxInspectorCapture.TargetState(true, false, false, false, false, null, null, false),
                        new UxInspectorCapture.Bounds(20, 40, 180, 42)),
                List.of(new UxInspectorCapture.Ancestor(1, "crm-contact-form", "form", "Kontakt", null,
                        Map.of("data-testid", "contact-form"))),
                null,
                new UxInspectorCapture.Traversal(4, 2, 2, true),
                new UxInspectorCapture.Signals(0, "TOP_LEVEL", List.of()), List.of(),
                new UxInspectorCapture.Client("TDW UX Inspector", "1.0.0", "ux-inspector"));
    }

    public static FrontendApplicationCatalog frontendCatalog() {
        return new FrontendApplicationCatalog("crm-catalog-v1", List.of(new FrontendApplicationRegistration(
                "crm-agent-portal", "CRM Agent Portal", "Fikcyjny portal obslugi klienta",
                "crm-ui", "CRM/crm-ui", "CRM", "crm-ui", "main", "ANGULAR", List.of("src/app")
        )), List.of());
    }

    public static GitLabFrontendReachabilityComponent component(String id, String testId, String label, int order) {
        var symbol = "Crm" + id.replaceAll("[^A-Za-z0-9]", "") + "Component";
        var source = "src/app/contacts/" + id + ".component.ts";
        var template = "src/app/contacts/" + id + ".component.html";
        var markup = "<button data-testid=\"" + testId + "\">" + label + "</button>";
        return new GitLabFrontendReachabilityComponent(id, order, 0, true, "ROUTE_TARGET", symbol,
                "crm-" + id, source, template, markup, "RESOLVED", List.of(), List.of(), List.of(),
                List.of(), List.of(), "export class " + symbol + " { execute() {} }", 120, 120,
                false, List.of());
    }

    public static GitLabFrontendScreenReachabilityGraph graph(List<GitLabFrontendReachabilityComponent> components) {
        var scope = new GitLabFrontendRepositoryScope("CRM", "crm-ui", "main", List.of("src/app"));
        var target = new GitLabFrontendRouteTarget("CrmContactCreateComponent", SOURCE_PATH);
        var screen = new GitLabFrontendScreenIdentity(VIEW_ID, "route-contact-create", "/contacts/new", "primary", target);
        var sourceRef = new GitLabFrontendSourceReference("src/app/app.routes.ts", "routes", 10, 18);
        var routeNode = new GitLabFrontendRouteNode("route-contact-create", null, screen, "Create contact",
                "contacts/new", "/contacts/new", "primary", GitLabFrontendRouteNodeKind.SCREEN,
                GitLabFrontendDiscoveryStatus.RESOLVED, false, List.of(), target, null, null, List.of(), sourceRef, List.of());
        var segment = new GitLabFrontendRouteChainSegment(routeNode.nodeId(), routeNode.pathSegment(),
                routeNode.routePattern(), "primary", List.of(), sourceRef);
        return new GitLabFrontendScreenReachabilityGraph(scope, new GitLabFrontendSourceRevision("main", REVISION),
                "READY", routeNode, new GitLabFrontendEffectiveRouteChain(screen, List.of(segment), List.of()),
                List.of(new GitLabFrontendReachabilityComponentLevel(0, components)), List.of(), List.of(), List.of(),
                components.size() * 2, 1_000, 600, 300, false, List.of(), "CRM graph");
    }

    public static UxInspectorTargetContext targetContext() {
        var component = component("contact-create", "contact-save", "Zapisz kontakt", 1);
        var graph = graph(List.of(component));
        var candidate = new UxInspectorTargetCandidate(component.componentId(), 100,
                List.of("stable attribute data-testid matched"), component.componentId(), component.symbol(),
                component.selector(), component.sourcePath(), component.templatePath(), 1,
                "FILE " + component.templatePath() + "#L1\n" + component.templateContent(),
                List.of(component.templatePath(), component.sourcePath()));
        return new UxInspectorTargetContext("crm-agent-portal", "CRM Agent Portal",
                new UxInspectorSourceScope("CRM", "crm-ui", "main", List.of("src/app")),
                new UxInspectorViewIdentity(VIEW_ID, "Create contact", "/contacts/new"),
                new UxInspectorSourceRevision("main", REVISION), UxInspectorTargetResolutionStatus.RESOLVED,
                List.of(candidate), new UxInspectorSourceBinding(component.componentId(), component.symbol(),
                component.selector(), component.sourcePath(), component.templatePath(), "EXTERNAL", 1, 1,
                component.templatePath() + "#L1-L1", component.templateContent(), List.of(), null, List.of()),
                candidate.sourceSlice(), List.of(), graph);
    }
}
