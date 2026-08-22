package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependency;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependencyCategory;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependencyKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponentLevel;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.StringUtils.countOccurrencesOf;

class UiExplorerSourceSliceRendererTest {

    private static final String SHARED_PATH =
            "libs/crm/data-access/src/lib/crm-contact-workspace.facade.ts";

    private final UiExplorerSourceSliceRenderer renderer = new UiExplorerSourceSliceRenderer();

    @Test
    void shouldLosslesslyGroupSlicesAndDeduplicateOnlyIdenticalEvidenceWithinOneCrmFile() {
        var repeatedBusinessLines = "  // CRM_SYNTHETIC_DETAIL_RETAINED\n".repeat(80);
        var sharedLoadSlice = """
                import { HttpClient } from '@angular/common/http';
                import { map } from 'rxjs';
                // ... 3 unrelated imports omitted ...
                export class CrmContactWorkspaceFacade {
                  loadContact() { return this.http.get('/synthetic-crm/contact').pipe(map(value => value)); }
                  readonly CRM_GROUPED_LOAD_MARKER = 'synthetic load';
                """ + repeatedBusinessLines + "}\n";
        var saveSlice = """
                import { HttpClient } from '@angular/common/http';
                import { filter } from 'rxjs';
                // ... 2 unrelated methods omitted ...
                export class CrmContactWorkspaceFacade {
                  saveContact() { return this.http.post('/synthetic-crm/contact', {}).pipe(filter(Boolean)); }
                  readonly CRM_GROUPED_SAVE_MARKER = 'synthetic save';
                }
                """;
        var dependencies = List.of(
                dependency("dependency-crm-contact-load", 0, "CrmContactWorkspaceFacade", "loadContact", sharedLoadSlice),
                dependency("dependency-crm-contact-save", 1, "CrmContactWorkspaceFacade", "saveContact", saveSlice),
                dependency("dependency-crm-contact-load-alias", 2, "CrmContactWorkspaceFacadeAlias", "loadContact", sharedLoadSlice),
                dependency("dependency-crm-contact-empty", 3, "CrmContactWorkspaceEmptySource", "refreshContact", "")
        );

        var rendered = renderer.render(graphWith(dependencies));

        assertThat(countOccurrencesOf(rendered, "`" + SHARED_PATH + "`")).isEqualTo(1);
        assertThat(countOccurrencesOf(rendered, "import { HttpClient } from '@angular/common/http';")).isEqualTo(1);
        assertThat(countOccurrencesOf(rendered, "import { map } from 'rxjs';")).isEqualTo(1);
        assertThat(countOccurrencesOf(rendered, "import { filter } from 'rxjs';")).isEqualTo(1);
        assertThat(countOccurrencesOf(rendered, "CRM_GROUPED_LOAD_MARKER")).isEqualTo(1);
        assertThat(countOccurrencesOf(rendered, "CRM_GROUPED_SAVE_MARKER")).isEqualTo(1);
        assertThat(countOccurrencesOf(rendered, "CRM_SYNTHETIC_DETAIL_RETAINED")).isEqualTo(80);
        assertThat(rendered)
                .contains("// ... 3 unrelated imports omitted ...")
                .contains("// ... 2 unrelated methods omitted ...")
                .contains("`dependency-crm-contact-load`, `dependency-crm-contact-load-alias`")
                .contains("`dependency-crm-contact-save`")
                .doesNotContain("dependency-crm-contact-empty")
                .doesNotContain("usedBy=", "downstream=", "members=", "entries=", "dependencies=")
                .doesNotContain("    // no source slice returned");

        var naiveRawSlices = UiExplorerAiPreparationTestFixture.context().graph().componentLevels().stream()
                .flatMap(level -> level.components().stream())
                .mapToInt(component -> component.sliceContent().length())
                .sum()
                + dependencies.stream().mapToInt(dependency -> dependency.sliceContent().length()).sum();
        assertThat(rendered.length())
                .as("file grouping should be shorter even than raw repeated slices without old per-slice metadata")
                .isLessThan(naiveRawSlices);
    }

    @Test
    void shouldUseASourceFenceLongerThanBackticksInsideSyntheticCrmEvidence() {
        var base = UiExplorerAiPreparationTestFixture.context().graph();
        var component = base.componentLevels().get(0).components().get(0);
        var sourceWithFence = """
                export class CrmContactPreferencesComponent {
                  readonly markdownFence = "```";
                }
                """;
        var replaced = new GitLabFrontendReachabilityComponent(
                component.componentId(), component.breadthFirstOrder(), component.depth(),
                component.connectedToSelectedScreen(), component.discoveryKind(), component.symbol(),
                component.selector(), component.sourcePath(), component.templatePath(), component.templateContent(),
                component.status(), component.templateBindings(), component.entrySymbols(), component.includedSymbols(),
                component.dependencyIds(), component.childComponentIds(), sourceWithFence,
                component.sourceCharacters(), sourceWithFence.length(), component.truncated(), component.limitations()
        );
        var graph = new GitLabFrontendScreenReachabilityGraph(
                base.scope(), base.sourceRevision(), base.status(), base.screenNode(), base.effectiveRouteChain(),
                List.of(new GitLabFrontendReachabilityComponentLevel(0, List.of(replaced))),
                List.of(), base.edges(), base.diagnostics(), base.sourceFileCount(), base.sourceCharacters(),
                sourceWithFence.length(), base.outlineCharacters(), base.contextLimitReached(), base.limitations(),
                base.readableOutline()
        );

        var rendered = renderer.render(graph);

        assertThat(rendered)
                .contains("````typescript")
                .contains("readonly markdownFence = \"```\";")
                .contains("\n````");
    }

    private GitLabFrontendReachabilityDependency dependency(
            String id,
            int order,
            String symbol,
            String method,
            String slice
    ) {
        return new GitLabFrontendReachabilityDependency(
                id,
                order,
                GitLabFrontendReachabilityDependencyKind.FACADE,
                GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL,
                symbol,
                SHARED_PATH,
                "@synthetic-crm/data-access",
                slice.isBlank() ? "UNRESOLVED" : "OK",
                List.of(method),
                List.of("component-crm-contact-preferences"),
                List.of(),
                slice,
                slice.length() + 600,
                slice.length(),
                false,
                List.of()
        );
    }

    private GitLabFrontendScreenReachabilityGraph graphWith(
            List<GitLabFrontendReachabilityDependency> dependencies
    ) {
        var base = UiExplorerAiPreparationTestFixture.context().graph();
        return new GitLabFrontendScreenReachabilityGraph(
                base.scope(),
                base.sourceRevision(),
                base.status(),
                base.screenNode(),
                base.effectiveRouteChain(),
                base.componentLevels(),
                dependencies,
                base.edges(),
                base.diagnostics(),
                base.sourceFileCount(),
                base.sourceCharacters(),
                base.sliceCharacters(),
                base.outlineCharacters(),
                base.contextLimitReached(),
                base.limitations(),
                base.readableOutline()
        );
    }
}
