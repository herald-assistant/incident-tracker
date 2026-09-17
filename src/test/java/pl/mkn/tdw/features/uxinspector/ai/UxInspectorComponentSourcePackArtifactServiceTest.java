package pl.mkn.tdw.features.uxinspector.ai;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetCandidate;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdge;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdgeKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorComponentSourcePackArtifactServiceTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final UxInspectorComponentSourcePackArtifactService service =
            new UxInspectorComponentSourcePackArtifactService(repositoryPort);

    @Test
    void shouldIncludeOnlyResolvedTargetToViewPathAndOmitSiblingFromPrompt() {
        var child = component("contact-editor", "contact-save", "Zapisz kontakt", 2);
        var sibling = component("contact-summary", "contact-summary", "Podsumowanie", 3);
        var root = component("contact-create", "contact-form", "Formularz kontaktu", 1);
        var context = contextWithGraph(List.of(child, sibling, root), List.of(
                edge(root, child, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD),
                edge(root, sibling, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD)), child,
                UxInspectorTargetResolutionStatus.RESOLVED);
        var contents = new LinkedHashMap<String, String>();
        contents.put(root.sourcePath(), "export class " + root.symbol() + " { readonly mode = 'create'; }");
        contents.put(root.templatePath(), "<crm-contact-editor></crm-contact-editor>");
        contents.put(child.sourcePath(), "export class " + child.symbol() + " { save() {} }");
        contents.put(child.templatePath(), "<button data-testid=\"contact-save\">Zapisz kontakt</button>");
        contents.put(sibling.sourcePath(), "export class " + sibling.symbol() + " {}");
        contents.put(sibling.templatePath(), "<p>Podsumowanie</p>");
        stubFiles(contents);

        var artifact = service.prepare(context, capture());

        assertThat(artifact.graphComponentCount()).isEqualTo(3);
        assertThat(artifact.focusedComponentCount()).isEqualTo(2);
        assertThat(artifact.omittedGraphComponentCount()).isEqualTo(1);
        assertThat(artifact.fileCount()).isEqualTo(4);
        assertThat(artifact.availableFileCount()).isEqualTo(4);
        assertThat(artifact.unavailableFileCount()).isZero();
        assertThat(artifact.availableSourcePaths()).containsExactlyInAnyOrder(
                root.sourcePath(), root.templatePath(), child.sourcePath(), child.templatePath());
        assertThat(artifact.markdown())
                .contains("semantics: STATIC_SCREEN_REACHABILITY_NOT_RUNTIME_ANCESTRY")
                .contains("version: 2", "scope: SELECTED_TARGET_TO_VIEW_PATHS_ONLY")
                .contains("graphComponentCount: 3", "focusedComponentCount: 2", "omittedGraphComponentCount: 1")
                .contains("fullSourceStrategy: RESOLVED_SHORTEST_TARGET_TO_VIEW_PATH")
                .contains("targetToView=contact-editor -> contact-create")
                .contains("| # | componentId | depth | bfs | symbol | selector | discovery | status | sourceMode")
                .contains("| 1 | contact-create |", "| 2 | contact-editor |")
                .contains("## Focused component relations", "relationCount: 1")
                .contains("contact-create --TEMPLATE_CHILD--> contact-editor")
                .contains("BEGIN_UNTRUSTED_COMPONENT_FILE " + root.sourcePath())
                .contains(contents.get(root.sourcePath()), contents.get(child.templatePath()))
                .doesNotContain(sibling.componentId(), sibling.sourcePath(), contents.get(sibling.sourcePath()), "INDEX_ONLY")
                .doesNotContain("dependencyIds:", "childComponentIds:", "incomingEdges:", "outgoingEdges:")
                .contains("status: AVAILABLE", "complete: true");
        assertThat(artifact.markdown().indexOf("| 1 | contact-create |"))
                .isLessThan(artifact.markdown().indexOf("| 2 | contact-editor |"));
        verify(repositoryPort, never()).readFileMetadata("CRM", "crm-ui", REVISION, sibling.sourcePath());
        verify(repositoryPort, never()).readFileMetadata("CRM", "crm-ui", REVISION, sibling.templatePath());
    }

    @Test
    void shouldRenderEachFocusedGraphRelationOnceAndOmitRelationsOutsideTheSelectedPath() {
        var target = component("contact-editor", "contact-save", "Zapisz kontakt", 2);
        var root = withRelations(component("contact-create", "contact-form", "Formularz kontaktu", 1),
                List.of("dependency-contact-api"), List.of(target.componentId(), "contact-help"));
        var duplicatedEdge = edge(root, target, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD);
        var context = contextWithGraph(List.of(root, target), List.of(duplicatedEdge, duplicatedEdge), target,
                UxInspectorTargetResolutionStatus.RESOLVED);
        var contents = new LinkedHashMap<String, String>();
        for (var component : List.of(root, target)) {
            contents.put(component.sourcePath(), "export class " + component.symbol() + " {}");
            contents.put(component.templatePath(), "<p>" + component.componentId() + "</p>");
        }
        stubFiles(contents);

        var artifact = service.prepare(context, capture());

        assertThat(artifact.markdown())
                .contains("relationCount: 1")
                .containsOnlyOnce("contact-create --TEMPLATE_CHILD--> contact-editor")
                .doesNotContain("contact-help", "dependency-contact-api", "DECLARED_CHILD", "DECLARED_DEPENDENCY");
    }

    @Test
    void shouldOmitComponentsAndRelationsOutsideTheFocusedPathFromALargeGraph() {
        var root = component("contact-create", "contact-form", "Formularz kontaktu", 1);
        var target = component("contact-editor", "contact-save", "Zapisz kontakt", 2);
        var components = new ArrayList<pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent>();
        var edges = new ArrayList<GitLabFrontendReachabilityEdge>();
        components.add(root);
        components.add(target);
        edges.add(edge(root, target, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD));
        for (var index = 3; index <= 101; index++) {
            var sibling = component("contact-section-" + index, "contact-section-" + index,
                    "Sekcja kontaktu " + index, index);
            components.add(sibling);
            edges.add(edge(root, sibling, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD));
        }
        var context = contextWithGraph(components, edges, target, UxInspectorTargetResolutionStatus.RESOLVED);
        var contents = new LinkedHashMap<String, String>();
        for (var component : List.of(root, target)) {
            contents.put(component.sourcePath(), "export class " + component.symbol() + " {}");
            contents.put(component.templatePath(), "<p>" + component.componentId() + "</p>");
        }
        stubFiles(contents);

        var artifact = service.prepare(context, capture());

        assertThat(artifact.graphComponentCount()).isEqualTo(101);
        assertThat(artifact.focusedComponentCount()).isEqualTo(2);
        assertThat(artifact.omittedGraphComponentCount()).isEqualTo(99);
        assertThat(artifact.markdown())
                .contains("graphComponentCount: 101", "focusedComponentCount: 2",
                        "omittedGraphComponentCount: 99", "relationCount: 1")
                .containsOnlyOnce("contact-create --TEMPLATE_CHILD--> contact-editor")
                .doesNotContain("contact-section-3", "contact-section-101", "INDEX_ONLY");
        assertThat(artifact.markdown().length()).isLessThan(10_000);
    }

    @Test
    void shouldIgnoreComponentReferenceShortcutWhenSelectingRenderingPath() {
        var root = component("contact-create", "contact-form", "Formularz kontaktu", 1);
        var middle = component("contact-section", "contact-section", "Sekcja", 2);
        var target = component("contact-editor", "contact-save", "Zapisz kontakt", 3);
        var context = contextWithGraph(List.of(root, middle, target), List.of(
                edge(root, middle, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD),
                edge(middle, target, GitLabFrontendReachabilityEdgeKind.ROUTED_CHILD),
                edge(root, target, GitLabFrontendReachabilityEdgeKind.COMPONENT_REFERENCE)), target,
                UxInspectorTargetResolutionStatus.RESOLVED);
        var contents = new LinkedHashMap<String, String>();
        for (var component : List.of(root, middle, target)) {
            contents.put(component.sourcePath(), "export class " + component.symbol() + " {}");
            contents.put(component.templatePath(), "<p>" + component.componentId() + "</p>");
        }
        stubFiles(contents);

        var artifact = service.prepare(context, capture());

        assertThat(artifact.focusedComponentCount()).isEqualTo(3);
        assertThat(artifact.omittedGraphComponentCount()).isZero();
        assertThat(artifact.markdown())
                .contains("targetToView=contact-editor -> contact-section -> contact-create")
                .doesNotContain("targetToView=contact-editor -> contact-create");
    }

    @Test
    void shouldUnionBestPathsForAmbiguousCandidatesAndOmitUnrelatedComponent() {
        var root = component("contact-create", "contact-form", "Formularz kontaktu", 1);
        var first = component("contact-editor", "contact-save", "Zapisz kontakt", 2);
        var second = component("contact-owner", "contact-owner", "Opiekun", 3);
        var unrelated = component("contact-help", "contact-help", "Pomoc", 4);
        var base = contextWithGraph(List.of(root, first, second, unrelated), List.of(
                edge(root, first, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD),
                edge(root, second, GitLabFrontendReachabilityEdgeKind.DYNAMIC_COMPONENT),
                edge(root, unrelated, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD)), first,
                UxInspectorTargetResolutionStatus.AMBIGUOUS);
        var candidates = List.of(candidate(first, 100), candidate(second, 90));
        var context = new UxInspectorTargetContext(
                base.systemId(), base.systemLabel(), base.sourceScope(), base.view(), base.sourceRevision(),
                UxInspectorTargetResolutionStatus.AMBIGUOUS, candidates, base.sourceBinding(),
                base.focusedSourceSlice(), base.limitations(), base.graph());
        var contents = new LinkedHashMap<String, String>();
        for (var component : List.of(root, first, second, unrelated)) {
            contents.put(component.sourcePath(), "export class " + component.symbol() + " {}");
            contents.put(component.templatePath(), "<p>" + component.componentId() + "</p>");
        }
        stubFiles(contents);

        var artifact = service.prepare(context, capture());

        assertThat(artifact.focusedComponentCount()).isEqualTo(3);
        assertThat(artifact.omittedGraphComponentCount()).isEqualTo(1);
        assertThat(artifact.markdown())
                .contains("fullSourceStrategy: AMBIGUOUS_UNION_OF_UP_TO_THREE_TARGET_TO_VIEW_PATHS")
                .contains("targetToView=contact-editor -> contact-create")
                .contains("targetToView=contact-owner -> contact-create")
                .doesNotContain(unrelated.componentId(), unrelated.sourcePath(), "INDEX_ONLY");
        verify(repositoryPort, never()).readFileMetadata("CRM", "crm-ui", REVISION, unrelated.sourcePath());
    }

    @Test
    void shouldIncludeOnlySelectedViewSourceWhenTargetWasNotFound() {
        var root = component("contact-create", "contact-form", "Formularz kontaktu", 1);
        var child = component("contact-editor", "contact-save", "Zapisz kontakt", 2);
        var context = contextWithGraph(List.of(root, child),
                List.of(edge(root, child, GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD)), child,
                UxInspectorTargetResolutionStatus.NOT_FOUND);
        var contents = new LinkedHashMap<String, String>();
        contents.put(root.sourcePath(), "export class " + root.symbol() + " {}");
        contents.put(root.templatePath(), "<crm-contact-editor></crm-contact-editor>");
        contents.put(child.sourcePath(), "export class " + child.symbol() + " {}");
        contents.put(child.templatePath(), "<button>Zapisz</button>");
        stubFiles(contents);

        var artifact = service.prepare(context, capture());

        assertThat(artifact.focusedComponentCount()).isEqualTo(1);
        assertThat(artifact.omittedGraphComponentCount()).isEqualTo(1);
        assertThat(artifact.availableSourcePaths()).containsExactlyInAnyOrder(root.sourcePath(), root.templatePath());
        assertThat(artifact.markdown())
                .contains("fullSourceStrategy: NOT_FOUND_VIEW_COMPONENT_ONLY")
                .contains("targetToView=contact-create")
                .contains("complete: false")
                .doesNotContain("| contact-editor |", child.sourcePath(), "INDEX_ONLY");
    }

    @Test
    void shouldNotPromoteAnArbitraryComponentWhenSelectedViewIsMissingFromGraph() {
        var child = component("contact-editor", "contact-save", "Zapisz kontakt", 1);
        var context = contextWithGraph(List.of(child), List.of(), child,
                UxInspectorTargetResolutionStatus.RESOLVED);

        var artifact = service.prepare(context, capture());

        assertThat(artifact.focusedComponentCount()).isZero();
        assertThat(artifact.omittedGraphComponentCount()).isEqualTo(1);
        assertThat(artifact.fileCount()).isZero();
        assertThat(artifact.markdown())
                .contains("fullSourceStrategy: VIEW_COMPONENT_NOT_FOUND")
                .contains("no arbitrary graph component was promoted to full source")
                .doesNotContain(child.componentId(), child.sourcePath(), "INDEX_ONLY")
                .contains("complete: false");
        verify(repositoryPort, never()).readFileMetadata(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldKeepThePackUsableAndMarkEveryMissingPinnedFileWithoutBlockingPreparation() {
        var artifact = service.prepare(targetContext(), capture());

        assertThat(artifact.graphComponentCount()).isEqualTo(1);
        assertThat(artifact.focusedComponentCount()).isEqualTo(1);
        assertThat(artifact.omittedGraphComponentCount()).isZero();
        assertThat(artifact.fileCount()).isEqualTo(2);
        assertThat(artifact.availableFileCount()).isZero();
        assertThat(artifact.unavailableFileCount()).isEqualTo(2);
        assertThat(artifact.markdown())
                .contains("complete: false")
                .contains("status: UNAVAILABLE")
                .contains("reason: VERIFIED_PINNED_READ_FAILED")
                .contains("tag=crm-contact-form status=NOT_FOUND_IN_STATIC_GRAPH");
    }

    private UxInspectorTargetContext contextWithGraph(
            List<pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent> components,
            List<GitLabFrontendReachabilityEdge> edges,
            pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent target,
            UxInspectorTargetResolutionStatus status
    ) {
        var original = targetContext();
        var base = graph(components);
        var graph = new GitLabFrontendScreenReachabilityGraph(
                base.scope(), base.sourceRevision(), base.status(), base.screenNode(), base.effectiveRouteChain(),
                base.componentLevels(), base.dependencies(), edges, base.diagnostics(), base.sourceFileCount(),
                base.sourceCharacters(), base.sliceCharacters(), base.outlineCharacters(), base.contextLimitReached(),
                base.limitations(), base.readableOutline());
        var candidate = candidate(target, 100);
        return new UxInspectorTargetContext(
                original.systemId(), original.systemLabel(), original.sourceScope(), original.view(),
                original.sourceRevision(), status, List.of(candidate), original.sourceBinding(),
                original.focusedSourceSlice(), original.limitations(), graph);
    }

    private UxInspectorTargetCandidate candidate(
            pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent component,
            int score
    ) {
        return new UxInspectorTargetCandidate(component.componentId(), score, List.of("test match"),
                component.componentId(), component.symbol(), component.selector(), component.sourcePath(),
                component.templatePath(), 1, component.sliceContent(),
                List.of(component.sourcePath(), component.templatePath()));
    }

    private GitLabFrontendReachabilityEdge edge(
            pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent parent,
            pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent child,
            GitLabFrontendReachabilityEdgeKind kind
    ) {
        return new GitLabFrontendReachabilityEdge(parent.componentId(), child.componentId(), kind,
                child.selector(), parent.templatePath(), parent.symbol(), null);
    }

    private pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent withRelations(
            pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent component,
            List<String> dependencyIds,
            List<String> childComponentIds
    ) {
        return new pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent(
                component.componentId(), component.breadthFirstOrder(), component.depth(),
                component.connectedToSelectedScreen(), component.discoveryKind(), component.symbol(),
                component.selector(), component.sourcePath(), component.templatePath(), component.templateContent(),
                component.status(), component.templateBindings(), component.entrySymbols(), component.includedSymbols(),
                dependencyIds, childComponentIds, component.sliceContent(), component.sourceCharacters(),
                component.returnedCharacters(), component.truncated(), component.limitations());
    }

    private void stubFiles(Map<String, String> contents) {
        when(repositoryPort.readFileMetadata(eq("CRM"), eq("crm-ui"), eq(REVISION), anyString()))
                .thenAnswer(invocation -> {
                    var path = invocation.getArgument(3, String.class);
                    var content = contents.get(path);
                    if (content == null) return null;
                    var size = (long) content.getBytes(StandardCharsets.UTF_8).length;
                    return new GitLabRepositoryFileMetadata(
                            "CRM", "crm-ui", REVISION, path, "blob-" + path, REVISION, REVISION,
                            null, null, size);
                });
        when(repositoryPort.readFileBounded(eq("CRM"), eq("crm-ui"), eq(REVISION), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    var path = invocation.getArgument(3, String.class);
                    var content = contents.get(path);
                    return content == null ? null : new GitLabRepositoryFileContent(
                            "CRM", "crm-ui", REVISION, path, content, false);
                });
    }
}
