package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.EMBEDDED_COMPONENT_PATH;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.FETCHED_VALIDATOR_PATH;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;

class UiExplorerCopilotPoliciesTest {

    private final UiExplorerCopilotScopePolicy scopePolicy =
            new UiExplorerCopilotScopePolicy(new ObjectMapper());

    @Test
    void shouldAllowOneTargetedCrmSearchAndRejectScopeExpansion() {
        var context = new UiExplorerCopilotToolSessionContextFactory().create("crm-scope-run", context());
        var valid = """
                {
                  "projectNames": [],
                  "pathPrefixes": ["apps/crm-agent"],
                  "branchRef": "main",
                  "keywords": ["CrmPreferenceValidator"],
                  "reason": "Uzupelnienie jednej luki walidacji CRM."
                }
                """;

        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context, GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES, valid
        ))).doesNotThrowAnyException();
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                valid.replace("main", "other-ref")
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("branchRef");
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                valid.replace("apps/crm-agent", "outside-scope")
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("pathPrefixes");
    }

    @Test
    void shouldAllowFocusedNewFileReadButRejectRedundantEmbeddedFileAndMissingReason() {
        var context = new UiExplorerCopilotToolSessionContextFactory().create("crm-read-run", context());
        var validChunk = chunk(FETCHED_VALIDATOR_PATH, "main", "Weryfikacja walidatora CRM.");

        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK, validChunk
        ))).doesNotThrowAnyException();
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                chunk(EMBEDDED_COMPONENT_PATH, "main", "Ponowny odczyt CRM.")
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("already complete");
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                chunk(FETCHED_VALIDATOR_PATH, "main", "")
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void shouldEnforceOneSearchAndTwoReadsPerCrmSession() {
        var context = new UiExplorerCopilotToolSessionContextFactory().create("crm-budget-run", context());
        var budget = new UiExplorerCopilotBudgetPolicy();

        budget.beforeInvocation(request(context, GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES, "{}"));
        budget.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK, "{}"));
        budget.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE, "{}"));

        assertThatThrownBy(() -> budget.beforeInvocation(request(
                context, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK, "{}"
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("budget");
        budget.clearSession(context.copilotSessionId());
        assertThatCode(() -> budget.beforeInvocation(request(
                context, GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES, "{}"
        ))).doesNotThrowAnyException();
    }

    private CopilotToolInvocationPolicyRequest request(
            pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext context,
            String toolName,
            String arguments
    ) {
        return new CopilotToolInvocationPolicyRequest(
                context,
                context.copilotSessionId(),
                "crm-tool-call-1",
                toolName,
                arguments
        );
    }

    private String chunk(String path, String branch, String reason) {
        return """
                {
                  "projectName": "crm-agent-portal",
                  "branchRef": "%s",
                  "filePath": "%s",
                  "startLine": 1,
                  "endLine": 80,
                  "maxCharacters": 4000,
                  "reason": "%s"
                }
                """.formatted(branch, path, reason);
    }
}
