package pl.mkn.tdw.agenttools.gitlab;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevisionPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GitLabRepositoryToolScopeTest {

    @Test void comparedProjectsKeepBothRevisionsAndDenyAThirdWithoutCallingGitLab() {
        var port=mock(GitLabRepositoryRevisionPort.class);
        var scope=GitLabRepositoryToolScope.forRevisions("CRM",java.util.List.of(
                new GitLabRepositoryToolScope.PinnedRevision("customer","refs/heads/release","a".repeat(40)),
                new GitLabRepositoryToolScope.PinnedRevision("customer","refs/tags/release","b".repeat(40)),
                new GitLabRepositoryToolScope.PinnedRevision("data","refs/heads/release","c".repeat(40))));
        assertThat(scope.resolve("customer","refs/heads/release",port).readRef()).isEqualTo("a".repeat(40));
        assertThat(scope.resolve("customer","refs/tags/release",port).readRef()).isEqualTo("b".repeat(40));
        assertThatThrownBy(()->scope.resolve("customer","main",port)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->scope.resolve("data","main",port)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(port);
        org.mockito.Mockito.when(port.resolveRevision("CRM","shared","main")).thenReturn(
                new pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryRevision("CRM","shared","main","d".repeat(40),null));
        assertThat(scope.resolve("shared","main",port).readRef()).isEqualTo("d".repeat(40));
        scope.resolve("shared","main",port);
        org.mockito.Mockito.verify(port,org.mockito.Mockito.times(1)).resolveRevision("CRM","shared","main");
    }

    private static final String COMMIT = "1234567890abcdef1234567890abcdef12345678";

    @Test
    void comparisonAllowsItsOwnCommitIdsWithoutResolvingNewRevisions() {
        var port = mock(GitLabRepositoryRevisionPort.class);
        var selected = java.util.List.of(
                new GitLabRepositoryToolScope.PinnedRevision("customer", "refs/heads/current", "a".repeat(40)),
                new GitLabRepositoryToolScope.PinnedRevision("customer", "refs/heads/next", "b".repeat(40)),
                new GitLabRepositoryToolScope.PinnedRevision("catalog", "refs/heads/next", "c".repeat(40)));
        var scope = GitLabRepositoryToolScope.forRevisions("CRM", selected);
        scope.restoreResearchRevisions(selected);
        assertThat(scope.resolve("customer", "a".repeat(40), port).readRef()).isEqualTo("a".repeat(40));
        assertThat(scope.resolve("CRM/customer", "B".repeat(40), port).readRef()).isEqualTo("b".repeat(40));
        assertThatThrownBy(() -> scope.resolve("customer", "c".repeat(40), port)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scope.resolve("customer", "d".repeat(40), port)).isInstanceOf(IllegalArgumentException.class);
        assertThat(scope.pinnedRevisions()).hasSize(3);
        verifyNoInteractions(port);
    }

    @Test
    void branchScopeReadsOnlyTheSelectedProjectWithoutResolvingACommit() {
        var revisions = mock(GitLabRepositoryRevisionPort.class);
        var scope = GitLabRepositoryToolScope.forBranch("CRM", "portal", "main");

        var target = scope.resolve("portal", "main", revisions);

        assertThat(target.readRef()).isEqualTo("main");
        assertThat(target.responseCommitId()).isNull();
        assertThat(scope.recordRead(target, "src/app.ts"))
                .isEqualTo("gitlab:CRM/portal@main:src/app.ts");
        assertThatThrownBy(() -> scope.resolve("other", "main", revisions))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scope.resolve("portal", "release", revisions))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(revisions);
    }

    @Test
    void pinnedScopeStillUsesItsSelectedCommit() {
        var scope = new GitLabRepositoryToolScope("CRM", "portal", "main", COMMIT);
        var target = scope.resolve("portal", "main", mock(GitLabRepositoryRevisionPort.class));

        assertThat(target.readRef()).isEqualTo(COMMIT);
        assertThat(target.responseCommitId()).isEqualTo(COMMIT);
    }
}
