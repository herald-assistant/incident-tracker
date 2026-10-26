package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabDiffToolScope;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot.FileDiff;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GitLabDiffMcpToolsTest {
    @Test
    void registeredCallbackIsVisibleToRuntime() {
        try (var context = new AnnotationConfigApplicationContext(GitLabDiffMcpTools.class, GitLabDiffMcpToolConfiguration.class)) {
            assertThat(context.getBeansOfType(ToolCallbackProvider.class).values())
                    .flatExtracting(provider -> List.of(provider.getToolCallbacks()))
                    .extracting(callback -> callback.getToolDefinition().name())
                    .containsExactly("gitlab_read_merge_request_diff");
        }
    }

    @Test
    void focusedPatchContinuationKeepsVersionAndBeforeAfterPaths() {
        var tools = new GitLabDiffMcpTools();
        var context = context(List.of(new FileDiff("Old.java", "New.java", false, true, false, "-old\n+new", false, false)));
        var first = tools.readMergeRequestDiff("customer-api", 7, List.of("Old.java"), null, null, 4, "Sprawdzam zmiane.", context);
        assertThat(first.files().get(0).patch()).isEqualTo("-old");
        assertThat(first.files().get(0).renamedFile()).isTrue();
        assertThat(first.complete()).isFalse();
        var second = tools.readMergeRequestDiff("customer-api", 7, List.of("Old.java"), first.nextFileOffset(),
                first.nextPatchOffset(), 100, "Doczytuje patch.", context);
        assertThat(second.files().get(0).patch()).isEqualTo("\n+new");
        assertThat(second.versionId()).isEqualTo(first.versionId());
        assertThat(second.baseSha()).isEqualTo("a".repeat(40));
        assertThat(second.headSha()).isEqualTo("c".repeat(40));
        assertThat(second.nextFileOffset()).isNull();
    }

    @Test
    void absentBinaryAndGitLabLimitedPatchesAreExplicitGaps() {
        var tools = new GitLabDiffMcpTools();
        var context = context(List.of(new FileDiff("logo.png", "logo.png", false, false, false, "", false, false),
                new FileDiff("Old.java", "Old.java", false, false, true, "", true, true)));
        var result = tools.readMergeRequestDiff("customer-api", 7, null, null, null, null, "Sprawdzam zmiane.", context);
        assertThat(result.complete()).isFalse();
        assertThat(result.files()).allMatch(GitLabDiffMcpTools.PatchSlice::unavailable);
        assertThat(result.files().get(1).deletedFile()).isTrue();
        assertThat(result.limitations()).hasSize(2);
        var absent = tools.readMergeRequestDiff("customer-api", 7, List.of("Missing.java"), null, null, null, "Sprawdzam plik.", context);
        assertThat(absent.complete()).isFalse();
        assertThat(absent.limitations()).anyMatch(l -> l.contains("Missing.java"));
    }

    @Test
    void rejectsForeignMrProjectAndInvalidContinuation() {
        var tools = new GitLabDiffMcpTools();
        var context = context(List.of());
        assertThatThrownBy(() -> tools.readMergeRequestDiff("other-api", 7, null, null, null, null, "Sprawdzam.", context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.readMergeRequestDiff("customer-api", 8, null, null, null, null, "Sprawdzam.", context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.readMergeRequestDiff("customer-api", 7, null, -1, null, null, "Sprawdzam.", context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ToolContext context(List<FileDiff> files) {
        return new ToolContext(Map.of(AgentToolContextKeys.GITLAB_DIFF_SCOPE,
                new GitLabDiffToolScope("CRM", List.of(new GitLabMergeRequestDiffSnapshot("CRM/customer-api", 7, 11L,
                        "a".repeat(40), "b".repeat(40), "c".repeat(40), files, List.of())))));
    }
}
