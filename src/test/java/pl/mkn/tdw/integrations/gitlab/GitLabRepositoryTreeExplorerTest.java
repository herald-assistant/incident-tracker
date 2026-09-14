package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitLabRepositoryTreeExplorerTest {

    private final GitLabRepositoryPort port = mock(GitLabRepositoryPort.class);
    private final GitLabRepositoryTreeExplorer explorer = new GitLabRepositoryTreeExplorer(port);

    @Test
    void traversesFourLevelsFromMonorepoRootWithoutReadingFileContents() {
        page("", "", node("Backend", "tree"), node("README.md", "blob"),
                node(".env", "blob"), node("Backend/nested.txt", "blob"));
        page("Backend", "", node("Backend/crm-customer-api", "tree"));
        page("Backend/crm-customer-api", "", node("Backend/crm-customer-api/src", "tree"));
        page("Backend/crm-customer-api/src", "", node("Backend/crm-customer-api/src/main", "tree"));

        var tree = explorer.explore("group", "project", "1".repeat(40), "", "");

        assertThat(tree.depth()).isEqualTo(4);
        assertThat(tree.entries()).extracting(GitLabRepositoryTreeSlice.Entry::path)
                .containsExactly("Backend", "README.md", ".env", "Backend/crm-customer-api",
                        "Backend/crm-customer-api/src", "Backend/crm-customer-api/src/main");
        assertThat(tree.truncated()).isFalse();
        verify(port).listRepositoryTreeChildrenPage(eq("group"), eq("project"), eq("1".repeat(40)),
                eq("Backend/crm-customer-api/src"), eq(""), anyInt());
    }

    @Test
    void exposesContinuationForIncompleteDirectoryPage() {
        when(port.listRepositoryTreeChildrenPage(eq("group"), eq("project"), eq("commit"),
                eq("Frontend"), eq(""), anyInt()))
                .thenReturn(new GitLabRepositoryTreePage(List.of(node("Frontend/app", "tree")), "nextCursor"));
        page("Frontend/app", "", node("Frontend/app/src", "tree"));
        page("Frontend/app/src", "", node("Frontend/app/src/index.ts", "blob"));

        var tree = explorer.explore("group", "project", "commit", "Frontend", "");

        assertThat(tree.truncated()).isTrue();
        assertThat(tree.continuations()).contains(
                new GitLabRepositoryTreeSlice.Continuation("Frontend", "nextCursor"));
        assertThat(tree.entries()).extracting(GitLabRepositoryTreeSlice.Entry::path)
                .contains("Frontend/app/src/index.ts");
    }

    @Test
    void rejectsTraversalPathsAndInvalidCursorBeforeCallingGitLab() {
        assertThatThrownBy(() -> explorer.explore("group", "project", "commit", "../other", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> explorer.explore("group", "project", "commit", "", "wrong/cursor"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(port);
    }

    @Test
    void navigatesDotDirectoriesAndPathsNamedSecrets() {
        page(".github", "", node(".github/copilot-instructions.md", "blob"));
        page("secrets", "", node("secrets/example.pem", "blob"));

        assertThat(explorer.explore("group", "project", "commit", ".github", "").entries())
                .extracting(GitLabRepositoryTreeSlice.Entry::path)
                .containsExactly(".github/copilot-instructions.md");
        assertThat(explorer.explore("group", "project", "commit", "secrets", "").entries())
                .extracting(GitLabRepositoryTreeSlice.Entry::path)
                .containsExactly("secrets/example.pem");
    }

    @Test
    void rejectsPageThatViolatesTheRequestedEntryBound() {
        var tooMany = java.util.stream.IntStream.range(0, 41)
                .mapToObj(index -> node("file-" + index + ".md", "blob"))
                .toList();
        when(port.listRepositoryTreeChildrenPage(eq("group"), eq("project"), eq("commit"),
                eq(""), eq(""), eq(40)))
                .thenReturn(new GitLabRepositoryTreePage(tooMany, null));

        assertThatThrownBy(() -> explorer.explore("group", "project", "commit", "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    private void page(String path, String cursor, GitLabRepositoryTreeNode... nodes) {
        when(port.listRepositoryTreeChildrenPage(eq("group"), eq("project"),
                org.mockito.ArgumentMatchers.anyString(), eq(path), eq(cursor), anyInt()))
                .thenReturn(new GitLabRepositoryTreePage(List.of(nodes), null));
    }

    private GitLabRepositoryTreeNode node(String path, String type) {
        return new GitLabRepositoryTreeNode(path, type);
    }
}
