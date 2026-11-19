package pl.mkn.tdw.agenttools.gitlab;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevisionPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryRevision;

import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitLabRepositoryToolScopeTest {
    private static final String FIRST = "a".repeat(40), SECOND = "b".repeat(40), THIRD = "c".repeat(40);

    @Test void preparedPinsRemainStableWhileOtherBranchesAndTagsAreAvailable() {
        var port = mock(GitLabRepositoryRevisionPort.class);
        var scope = GitLabRepositoryToolScope.forRevisions("CRM", List.of(
                new GitLabRepositoryToolScope.PinnedRevision("customer", "main", FIRST),
                new GitLabRepositoryToolScope.PinnedRevision("customer", "refs/tags/release", SECOND)));
        when(port.resolveRevision("CRM", "customer", "release/older")).thenReturn(
                new GitLabRepositoryRevision("CRM", "customer", "release/older", THIRD, null),
                new GitLabRepositoryRevision("CRM", "customer", "release/older", FIRST, null));
        when(port.resolveRevision("CRM", "customer", "refs/tags/older")).thenReturn(
                new GitLabRepositoryRevision("CRM", "customer", "refs/tags/older", THIRD, null));
        assertThat(scope.resolve("customer", "main", port).readRef()).isEqualTo(FIRST);
        assertThat(scope.resolve("customer", "refs/tags/release", port).readRef()).isEqualTo(SECOND);
        assertThat(scope.resolve("customer", "release/older", port).readRef()).isEqualTo(THIRD);
        assertThat(scope.resolve("customer", "release/older", port).readRef()).isEqualTo(THIRD);
        assertThat(scope.resolve("customer", "refs/tags/older", port).readRef()).isEqualTo(THIRD);
        verify(port, times(1)).resolveRevision("CRM", "customer", "release/older");
        verify(port, never()).resolveRevision("CRM", "customer", "main");
    }

    @Test void arbitraryFullShaNeedsNeitherHistoryGrantNorResolution() {
        var port = mock(GitLabRepositoryRevisionPort.class);
        var scope = new GitLabRepositoryToolScope("CRM", "customer", "main", FIRST);
        assertThat(scope.resolve("customer", THIRD.toUpperCase(), port).readRef()).isEqualTo(THIRD);
        assertThat(scope.resolve("CRM/customer", SECOND, port).responseCommitId()).isEqualTo(SECOND);
        verifyNoInteractions(port);
    }

    @Test void branchScopePinsEachProjectAndRefIndependently() {
        var port = mock(GitLabRepositoryRevisionPort.class);
        var scope = GitLabRepositoryToolScope.forBranch("CRM", "portal", "main");
        when(port.resolveRevision("CRM", "portal", "main")).thenReturn(
                new GitLabRepositoryRevision("CRM", "portal", "main", FIRST, null));
        when(port.resolveRevision("CRM", "portal", "release")).thenReturn(
                new GitLabRepositoryRevision("CRM", "portal", "release", SECOND, null));
        var target = scope.resolve("portal", "main", port);
        assertThat(target.responseCommitId()).isEqualTo(FIRST);
        assertThat(scope.recordRead(target, "src/app.ts")).isEqualTo("gitlab:CRM/portal@" + FIRST + ":src/app.ts");
        assertThat(scope.resolve("portal", "release", port).readRef()).isEqualTo(SECOND);
        when(port.resolveRevision("CRM", "services/contacts", "main")).thenReturn(
                new GitLabRepositoryRevision("CRM", "services/contacts", "main", THIRD, null));
        assertThat(scope.resolve("CRM/services/contacts", "main", port).readRef()).isEqualTo(THIRD);
        assertThat(scope.resolve("services/contacts", "main", port).readRef()).isEqualTo(THIRD);
        verify(port, times(1)).resolveRevision("CRM", "services/contacts", "main");
        assertThatThrownBy(() -> scope.resolve("../other", "main", port)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void continuationRestoresAdditionalRefsOfComparedProjectWithoutRepinning() {
        var scope = GitLabRepositoryToolScope.forRevisions("CRM", List.of(
                new GitLabRepositoryToolScope.PinnedRevision("customer", "main", FIRST)));
        scope.restoreResearchRevisions(List.of(
                new GitLabRepositoryToolScope.PinnedRevision("customer", "release/older", SECOND)));
        var port = mock(GitLabRepositoryRevisionPort.class);
        assertThat(scope.resolve("customer", "release/older", port).readRef()).isEqualTo(SECOND);
        assertThatThrownBy(() -> scope.restoreResearchRevisions(List.of(
                new GitLabRepositoryToolScope.PinnedRevision("customer", "main", THIRD))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(scope.resolve("customer", "main", port).readRef()).isEqualTo(FIRST);
        verifyNoInteractions(port);
    }
}
