package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.integrations.gitlab.*;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot.FileDiff;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextNavigationPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextNavigationTestCreator;
import pl.mkn.tdw.testsupport.agenttools.GitLabMcpToolsTestCreator;

import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitLabHistoryMcpToolsTest {
    private static final String HEAD = "c".repeat(40), SHA = "a".repeat(40), PARENT = "b".repeat(40);
    private static final String PHONE = "src/Phone.java", CONTACT = "src/Contact.java";

    @Test void springRegistersBothExecutableCallbacksWithoutModelFacingScope() {
        var f = new Fixture();
        f.page(PHONE, 1, List.of(commit()), null);
        try (var spring = new AnnotationConfigApplicationContext()) {
            spring.registerBean(GitLabCommitHistoryPort.class, () -> f.history);
            spring.registerBean(GitLabRepositoryRevisionPort.class, () -> f.repository);
            spring.registerBean(GitLabSettingsPort.class, () -> f.settings);
            spring.registerBean(OperationalContextPort.class, () -> mock(OperationalContextPort.class));
            spring.registerBean(OperationalContextNavigationPort.class, OperationalContextNavigationTestCreator::navigation);
            spring.register(GitLabHistoryMcpTools.class, GitLabHistoryMcpToolConfiguration.class);
            spring.refresh();
            var callbacks = spring.getBeansOfType(ToolCallbackProvider.class).values().stream()
                    .flatMap(p -> Arrays.stream(p.getToolCallbacks())).toList();
            assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
                    .containsExactlyInAnyOrder("gitlab_list_repository_commits", "gitlab_read_commit_diff");
            var list = callbacks.stream().filter(c -> c.getToolDefinition().name().equals("gitlab_list_repository_commits")).findFirst().orElseThrow();
            assertThat(list.getToolDefinition().inputSchema()).doesNotContain("gitLabGroup", "gitLabBranch", "branchRef", "environment", "toolContext");
            assertThat(list.call("{\"projectName\":\"customer\",\"filePaths\":[\"src/Phone.java\"],\"reason\":\"Sprawdzam historię.\"}", f.context))
                    .contains("CRM Integrator", "integrator@example.com", SHA, PHONE);
        }
    }

    @Test void mergesFilesDeduplicatesShaAndContinuesEachPathAtTheSamePinnedHead() {
        var f = new Fixture();
        f.page(PHONE, 1, List.of(commit()), 2);
        f.page(CONTACT, 1, List.of(commit()), null);
        f.page(PHONE, 2, List.of(new GitLabHistoryCommit(PARENT, List.of(), Instant.EPOCH, "Pierwsza wersja", null, null, null)), null);
        var first = f.list(List.of(PHONE, CONTACT), null);
        assertThat(first.commits()).hasSize(1);
        assertThat(first.commits().get(0).matchedPaths()).containsExactlyInAnyOrder(PHONE, CONTACT);
        assertThat(first.historyHeadSha()).isEqualTo(HEAD);
        var second = f.list(List.of(CONTACT, PHONE), first.nextCursor());
        assertThat(second.commits()).extracting(c -> c.commit().id()).containsExactly(PARENT);
        assertThat(second.nextCursor()).isNull();
        verify(f.repository, times(1)).resolveRevision("CRM", "customer", "main");
        verify(f.history, never()).listCommits("CRM", "customer", HEAD, CONTACT, null, null, 2);
        assertThatThrownBy(() -> f.list(List.of(PHONE), first.nextCursor())).isInstanceOf(IllegalArgumentException.class);
        var resumed = new Fixture();
        resumed.page(PHONE, 2, List.of(commit()), null);
        assertThat(resumed.list(List.of(PHONE, CONTACT), first.nextCursor()).commits()).hasSize(1);
    }

    @Test void partialFailureRetainsSuccessfulEvidenceAndRetriesOnlyMissingPage() {
        var f = new Fixture();
        f.page(PHONE, 1, List.of(commit()), null);
        when(f.history.listCommits("CRM", "customer", HEAD, CONTACT, null, null, 1))
                .thenThrow(new IllegalStateException("remote secret"))
                .thenReturn(new GitLabCommitPage(List.of(commit()), null));
        var first = f.list(List.of(PHONE, CONTACT), null);
        assertThat(first.commits()).hasSize(1);
        assertThat(first.limitations()).anyMatch(s -> s.contains(CONTACT)).noneMatch(s -> s.contains("remote secret"));
        assertThat(f.list(List.of(PHONE, CONTACT), first.nextCursor()).nextCursor()).isNull();
        verify(f.history, times(1)).listCommits("CRM", "customer", HEAD, PHONE, null, null, 1);
    }

    @Test void acceptsDirectCommitsAndArbitraryPathsWithoutHistoryGrants() {
        var f = new Fixture();
        f.page("", 1, List.of(commit()), null);
        assertThat(f.list(null, null).commits()).hasSize(1);
        f.page("other-module/Contact.java", 1, List.of(commit()), null);
        assertThat(f.list(List.of("other-module/Contact.java"), null).commits()).hasSize(1);
        when(f.history.readDiff("CRM", "customer", PARENT, 1))
                .thenReturn(new GitLabCommitDiffPage(List.of(), null));
        assertThat(f.tools.readCommitDiff("customer", PARENT, null, null, "Sprawdzam CRM.", f.context).commitId())
                .isEqualTo(PARENT);
        assertThatThrownBy(() -> f.list(List.of("src/../password.txt"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.list(IntStream.range(0, 11).mapToObj(i -> "src/" + i).toList(), null))
                .isInstanceOf(IllegalArgumentException.class);
        when(f.history.readDiff("CRM", "services/contacts", SHA, 1)).thenReturn(new GitLabCommitDiffPage(List.of(), null));
        assertThat(f.tools.readCommitDiff("services/contacts", SHA, null, null, "Sprawdzam CRM.", f.context).commitId())
                .isEqualTo(SHA);
        assertThatThrownBy(() -> f.tools.readCommitDiff("../other", SHA, null, null, "Sprawdzam CRM.", f.context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void canExploreAnotherRefWithoutChangingTheSessionDefault() {
        var f = new Fixture();
        var context = new ToolContext(Map.of(AgentToolContextKeys.GITLAB_BRANCH, "main",
                AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE, f.scope));
        when(f.repository.resolveRevision("CRM", "customer", "release/older"))
                .thenReturn(new GitLabRepositoryRevision("CRM", "customer", "release/older", PARENT, null));
        when(f.history.listCommits("CRM", "customer", PARENT, "", null, null, 1))
                .thenReturn(new GitLabCommitPage(List.of(commit()), null));
        assertThat(f.tools.listRepositoryCommits("customer", "release/older", null, null, null, null,
                "Sprawdzam wcześniejszą wersję CRM.", context).historyHeadSha()).isEqualTo(PARENT);
        assertThat(context.getContext().get(AgentToolContextKeys.GITLAB_BRANCH)).isEqualTo("main");
        assertThat(f.scope.selectedBranch()).isEqualTo("main");
    }

    @Test void diffSlicesContinueWithoutLosingFilesAndKeepUnavailablePatchesVisible() {
        var f = new Fixture(); f.page(PHONE, 1, List.of(commit()), null); f.list(List.of(PHONE), null);
        when(f.history.readDiff("CRM", "customer", SHA, 1)).thenReturn(new GitLabCommitDiffPage(List.of(
                new FileDiff(PHONE, PHONE, false, false, false, "x".repeat(20003), false, false),
                new FileDiff(CONTACT, CONTACT, false, false, true, null, true, true),
                new FileDiff("secret/Old.java", PHONE, false, true, false, "hidden", false, false)), 2));
        when(f.history.readDiff("CRM", "customer", SHA, 2)).thenReturn(new GitLabCommitDiffPage(List.of(), null));
        var first = f.tools.readCommitDiff("customer", SHA, null, null, "Sprawdzam.", f.context);
        assertThat(first.files()).hasSize(1);
        assertThat(first.files().get(0).patch()).hasSize(20000);
        var second = f.tools.readCommitDiff("customer", SHA, null, first.nextCursor(), "Sprawdzam.", f.context);
        assertThat(second.files()).hasSize(3);
        assertThat(second.files().get(0).patchOffset()).isEqualTo(20000);
        assertThat(second.files().get(1).deletedFile()).isTrue();
        assertThat(second.files().get(1).unavailable()).isTrue();
        assertThat(second.files()).anyMatch(p -> p.patch().contains("hidden"));
        var third = f.tools.readCommitDiff("customer", SHA, null, second.nextCursor(), "Sprawdzam.", f.context);
        assertThat(third.nextCursor()).isNull();
        assertThat(third.limitations()).isNotEmpty();
        verify(f.history, times(2)).readDiff("CRM", "customer", SHA, 1);
        assertThatThrownBy(() -> f.tools.readCommitDiff("customer", PARENT, null, first.nextCursor(), "Sprawdzam.", f.context)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void existingFileAndChunkToolsReadCommitsBeforeAnyHistoryCall() {
        var f = new Fixture();
        var reads = GitLabMcpToolsTestCreator.create(f.repository, f.settings);
        when(f.repository.readFileMetadata("CRM", "customer", SHA, PHONE)).thenReturn(new GitLabRepositoryFileMetadata(
                "CRM", "customer", SHA, PHONE, null, SHA, SHA, null, null, 3L));
        when(f.repository.readFileBounded(eq("CRM"), eq("customer"), eq(SHA), eq(PHONE), anyInt()))
                .thenReturn(new GitLabRepositoryFileContent("CRM", "customer", SHA, PHONE, "new", false));
        var file = reads.readRepositoryFile("customer", SHA, null, PHONE, null, "Sprawdzam CRM.", f.context);
        assertThat(file.content()).isEqualTo("new");
        assertThat(file.sourceRef()).contains(SHA);
        when(f.repository.readFileChunk("CRM", "customer", PARENT, PHONE, 1, 2, 100))
                .thenReturn(new GitLabRepositoryFileChunk("CRM", "customer", PARENT, PHONE, 1, 2, 1, 1, 1, "old", false));
        assertThat(reads.readRepositoryFileChunk("customer", PARENT, null, PHONE, 1, 2, 100, "Sprawdzam CRM.", f.context).content()).isEqualTo("old");
        verifyNoInteractions(f.history);
        assertThatThrownBy(() -> reads.readRepositoryFile("../other", SHA, null, PHONE, null, "Sprawdzam CRM.", f.context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GitLabHistoryCommit commit() {
        return new GitLabHistoryCommit(SHA, List.of(PARENT), Instant.parse("2026-10-01T00:00:00Z"), "Walidacja telefonu",
                "https://gitlab.example.com/CRM/customer/-/commit/" + SHA, "CRM Integrator", "integrator@example.com");
    }
    private static class Fixture {
        final GitLabCommitHistoryPort history = mock(GitLabCommitHistoryPort.class);
        final GitLabRepositoryPort repository = mock(GitLabRepositoryPort.class);
        final GitLabProperties settings = new GitLabProperties();
        final GitLabRepositoryToolScope scope = GitLabRepositoryToolScope.forBranch("CRM", "customer", "main");
        final ToolContext context = new ToolContext(Map.of(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE, scope));
        final GitLabHistoryMcpTools tools = new GitLabHistoryMcpTools(history, repository, settings,
                mock(OperationalContextPort.class), OperationalContextNavigationTestCreator.navigation());
        Fixture() { when(repository.resolveRevision("CRM", "customer", "main"))
                .thenReturn(new GitLabRepositoryRevision("CRM", "customer", "main", HEAD, null)); }
        void page(String path, int page, List<GitLabHistoryCommit> commits, Integer next) {
            when(history.listCommits("CRM", "customer", HEAD, path, null, null, page)).thenReturn(new GitLabCommitPage(commits, next));
        }
        GitLabHistoryMcpTools.CommitResult list(List<String> paths, String cursor) {
            return tools.listRepositoryCommits("customer", null, paths, null, null, cursor, "Sprawdzam zmianę.", context);
        }
    }
}
