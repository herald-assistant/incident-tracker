package pl.mkn.tdw.features.uxinspector.ai;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorContextException;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeNode;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreePage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.REVISION;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.targetContext;

class UxInspectorRepositoryTreeArtifactServiceTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final UxInspectorRepositoryTreeArtifactService service =
            new UxInspectorRepositoryTreeArtifactService(repositoryPort);

    @Test
    void shouldLoadEveryPageAndRenderOnlyTheFirstFourPathLevels() {
        when(repositoryPort.listRepositoryTreeChildrenPage("CRM", "crm-ui", REVISION, "", "", 100))
                .thenReturn(page(List.of(
                        node(".github", "tree"), node("README.md", "blob"), node("src", "tree")
                ), "root-2"));
        when(repositoryPort.listRepositoryTreeChildrenPage("CRM", "crm-ui", REVISION, "", "root-2", 100))
                .thenReturn(page(List.of(node("pom.xml", "blob")), null));
        when(repositoryPort.listRepositoryTreeChildrenPage("CRM", "crm-ui", REVISION, ".github", "", 100))
                .thenReturn(page(List.of(node(".github/copilot-instructions.md", "blob")), null));
        when(repositoryPort.listRepositoryTreeChildrenPage("CRM", "crm-ui", REVISION, "src", "", 100))
                .thenReturn(page(List.of(node("src/app", "tree")), null));
        when(repositoryPort.listRepositoryTreeChildrenPage("CRM", "crm-ui", REVISION, "src/app", "", 100))
                .thenReturn(page(List.of(node("src/app/pages", "tree")), null));
        when(repositoryPort.listRepositoryTreeChildrenPage("CRM", "crm-ui", REVISION, "src/app/pages", "", 100))
                .thenReturn(page(List.of(node("src/app/pages/login.ts", "blob"),
                        node("src/app/pages/private", "tree")), null));

        var artifact = service.prepare(targetContext());

        assertThat(artifact.markdown()).contains(
                "repository: CRM/crm-ui",
                "branch: main",
                "commit: " + REVISION,
                "depth: 4",
                "content: PATH_NAMES_ONLY",
                "complete: true",
                "- [dir] .github",
                "- [file] .github/copilot-instructions.md",
                "- [file] README.md",
                "- [file] pom.xml",
                "- [file] src/app/pages/login.ts",
                "- [dir] src/app/pages/private"
        );
        assertThat(artifact.filePaths()).containsExactly(
                ".github/copilot-instructions.md", "README.md", "pom.xml", "src/app/pages/login.ts");
        verify(repositoryPort, never()).listRepositoryTreeChildrenPage(
                "CRM", "crm-ui", REVISION, "src/app/pages/private", "", 100);
    }

    @Test
    void shouldFailInsteadOfPublishingAnIncompleteOrMalformedTree() {
        when(repositoryPort.listRepositoryTreeChildrenPage("CRM", "crm-ui", REVISION, "", "", 100))
                .thenReturn(page(List.of(node("src/nested/file.ts", "blob")), null));

        assertThatThrownBy(() -> service.prepare(targetContext()))
                .isInstanceOfSatisfying(UxInspectorContextException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("UX_INSPECTOR_REPOSITORY_TREE_UNAVAILABLE");
                    assertThat(exception.getMessage()).contains("complete four-level repository tree");
                });
    }

    private GitLabRepositoryTreePage page(List<GitLabRepositoryTreeNode> nodes, String cursor) {
        return new GitLabRepositoryTreePage(nodes, cursor);
    }

    private GitLabRepositoryTreeNode node(String path, String type) {
        return new GitLabRepositoryTreeNode(path, type);
    }
}
