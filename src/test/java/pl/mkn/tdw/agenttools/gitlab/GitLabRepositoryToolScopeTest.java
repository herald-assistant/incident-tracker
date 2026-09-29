package pl.mkn.tdw.agenttools.gitlab;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevisionPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GitLabRepositoryToolScopeTest {

    private static final String COMMIT = "1234567890abcdef1234567890abcdef12345678";

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
