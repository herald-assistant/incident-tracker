package pl.mkn.tdw.features.uxinspector.ai;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdge;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdgeKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorComponentSourcePackArtifactServiceTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final UxInspectorComponentSourcePackArtifactService service =
            new UxInspectorComponentSourcePackArtifactService(repositoryPort);

    @Test
    void shouldIncludeEveryDiscoveredComponentAndDeduplicatedFullPinnedFilesInGraphOrder() {
        var child = component("contact-editor", "contact-save", "Zapisz kontakt", 2);
        var root = component("contact-create", "contact-form", "Formularz kontaktu", 1);
        var context = contextWithGraph(List.of(child, root), List.of(new GitLabFrontendReachabilityEdge(
                root.componentId(), child.componentId(), GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD,
                child.selector(), root.templatePath(), root.symbol(), null)));
        var contents = new LinkedHashMap<String, String>();
        contents.put(root.sourcePath(), "export class " + root.symbol() + " { readonly mode = 'create'; }");
        contents.put(root.templatePath(), "<crm-contact-editor></crm-contact-editor>");
        contents.put(child.sourcePath(), "export class " + child.symbol() + " { save() {} }");
        contents.put(child.templatePath(), "<button data-testid=\"contact-save\">Zapisz kontakt</button>");
        stubFiles(contents);

        var artifact = service.prepare(context, capture());

        assertThat(artifact.componentCount()).isEqualTo(2);
        assertThat(artifact.fileCount()).isEqualTo(4);
        assertThat(artifact.availableFileCount()).isEqualTo(4);
        assertThat(artifact.unavailableFileCount()).isZero();
        assertThat(artifact.availableSourcePaths()).containsExactlyInAnyOrderElementsOf(contents.keySet());
        assertThat(artifact.markdown())
                .contains("semantics: STATIC_SCREEN_REACHABILITY_NOT_RUNTIME_ANCESTRY")
                .contains("componentId: contact-create", "componentId: contact-editor")
                .contains("contact-create --TEMPLATE_CHILD--> contact-editor")
                .contains("BEGIN_UNTRUSTED_COMPONENT_FILE " + root.sourcePath())
                .contains(contents.get(root.sourcePath()), contents.get(child.templatePath()))
                .contains("status: AVAILABLE", "complete: true");
        assertThat(artifact.markdown().indexOf("componentId: contact-create"))
                .isLessThan(artifact.markdown().indexOf("componentId: contact-editor"));
    }

    @Test
    void shouldKeepThePackUsableAndMarkEveryMissingPinnedFileWithoutBlockingPreparation() {
        var artifact = service.prepare(targetContext(), capture());

        assertThat(artifact.componentCount()).isEqualTo(1);
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
            List<GitLabFrontendReachabilityEdge> edges
    ) {
        var original = targetContext();
        var base = graph(components);
        var graph = new GitLabFrontendScreenReachabilityGraph(
                base.scope(), base.sourceRevision(), base.status(), base.screenNode(), base.effectiveRouteChain(),
                base.componentLevels(), base.dependencies(), edges, base.diagnostics(), base.sourceFileCount(),
                base.sourceCharacters(), base.sliceCharacters(), base.outlineCharacters(), base.contextLimitReached(),
                base.limitations(), base.readableOutline());
        return new UxInspectorTargetContext(
                original.systemId(), original.systemLabel(), original.sourceScope(), original.view(),
                original.sourceRevision(), original.status(), original.candidates(), original.sourceBinding(),
                original.focusedSourceSlice(), original.limitations(), graph);
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
