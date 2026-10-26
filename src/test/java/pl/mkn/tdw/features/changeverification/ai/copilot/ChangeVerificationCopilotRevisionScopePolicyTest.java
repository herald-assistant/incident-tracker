package pl.mkn.tdw.features.changeverification.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabDiffToolScope;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ChangeVerificationCopilotRevisionScopePolicyTest {
    private final ChangeVerificationCopilotRevisionScopePolicy policy = new ChangeVerificationCopilotRevisionScopePolicy(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"gitlab_list_repository_endpoints", "gitlab_build_endpoint_use_case_context",
            "gitlab_build_java_method_use_case_context", "gitlab_read_repository_file", "gitlab_read_repository_files_by_path",
            "gitlab_read_repository_file_chunk", "gitlab_read_repository_file_outline", "gitlab_read_java_method_slice",
            "gitlab_read_openapi_endpoint_slice"})
    void everySingleProjectReadRequiresExactPreparedProjectAndSha(String tool) {
        assertThatCode(() -> invoke(tool, single("customer-api", "c".repeat(40)))).doesNotThrowAnyException();
        assertThatCode(() -> invoke(tool, single("customer-api", "a".repeat(40)))).doesNotThrowAnyException();
        assertThatThrownBy(() -> invoke(tool, single("other-api", "c".repeat(40))))
                .isInstanceOf(CopilotToolInvocationRejectedException.class);
        assertThatThrownBy(() -> invoke(tool, single("customer-api", "main")))
                .isInstanceOf(CopilotToolInvocationRejectedException.class);
        assertThatThrownBy(() -> invoke(tool, single("customer-api", "d".repeat(40))))
                .isInstanceOf(CopilotToolInvocationRejectedException.class);
        assertThatThrownBy(() -> invoke(tool, single("other-api", "c".repeat(40)).replace("}", ",\"projectNames\":[\"customer-api\"]}")))
                .isInstanceOf(CopilotToolInvocationRejectedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gitlab_search_repository_candidates", "gitlab_find_class_references", "gitlab_find_flow_context"})
    void multiProjectSearchCannotExpandScope(String tool) {
        var args = "{\"projectNames\":[\"customer-api\"],\"branchRef\":\"%s\"}".formatted("c".repeat(40));
        assertThatCode(() -> invoke(tool, args)).doesNotThrowAnyException();
        assertThatThrownBy(() -> invoke(tool, args.replace("[\"customer-api\"]", "[]")))
                .isInstanceOf(CopilotToolInvocationRejectedException.class);
        assertThatThrownBy(() -> invoke(tool, args.replace("customer-api", "other-api")))
                .isInstanceOf(CopilotToolInvocationRejectedException.class);
    }

    @Test
    void chunkBatchValidatesEveryProjectAndDiffChecksIid() {
        var chunks = "{\"branchRef\":\"%s\",\"chunks\":[{\"projectName\":\"customer-api\"},{\"projectName\":\"other-api\"}]}"
                .formatted("c".repeat(40));
        assertThatThrownBy(() -> invoke("gitlab_read_repository_file_chunks", chunks))
                .isInstanceOf(CopilotToolInvocationRejectedException.class);
        assertThatCode(() -> invoke("gitlab_read_repository_file_chunks", chunks.replace("other-api", "customer-api")))
                .doesNotThrowAnyException();
        assertThatCode(() -> invoke("gitlab_read_merge_request_diff", "{\"projectName\":\"customer-api\",\"mergeRequestIid\":7}"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> invoke("gitlab_read_merge_request_diff", "{\"projectName\":\"customer-api\",\"mergeRequestIid\":8}"))
                .isInstanceOf(CopilotToolInvocationRejectedException.class);
    }

    private String single(String project, String ref) {
        return "{\"projectName\":\"%s\",\"branchRef\":\"%s\"}".formatted(project, ref);
    }

    private void invoke(String tool, String args) {
        var scope = new GitLabDiffToolScope("CRM", List.of(new GitLabMergeRequestDiffSnapshot("CRM/customer-api", 7, 11L,
                "a".repeat(40), "b".repeat(40), "c".repeat(40), List.of(), List.of())));
        var context = new CopilotToolSessionContext("run", "session", Map.of(
                ChangeVerificationCopilotToolContextKeys.FEATURE, ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE,
                AgentToolContextKeys.GITLAB_DIFF_SCOPE, scope));
        policy.beforeInvocation(new CopilotToolInvocationPolicyRequest(context, "session", "call", tool, args));
    }
}
