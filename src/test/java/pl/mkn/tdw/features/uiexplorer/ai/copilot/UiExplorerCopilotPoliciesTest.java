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

    private static final String API_PATH =
            "libs/crm/data-access/src/lib/crm-contact-preferences.api.ts";

    private final UiExplorerCopilotScopePolicy scopePolicy =
            new UiExplorerCopilotScopePolicy(new ObjectMapper());

    @Test
    void shouldAllowOneTargetedCrmSearchAndRejectScopeExpansion() {
        var context = new UiExplorerCopilotToolSessionContextFactory().create(
                "crm-scope-run",
                pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request(),
                context()
        );
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
    void shouldAllowPreparedRouteReferenceAndNaturalTypeScriptCoordinates() {
        var context = new UiExplorerCopilotToolSessionContextFactory().create(
                "crm-slice-run",
                pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request(),
                context()
        );

        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                routeSlice("crm-contact-preferences", "Potwierdzenie route chain CRM.")
        ))).doesNotThrowAnyException();
        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                directSlice(EMBEDDED_COMPONENT_PATH, "CrmContactPreferencesComponent", null,
                        "Potwierdzenie formularza CRM.")
        ))).doesNotThrowAnyException();
        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                importSlice(EMBEDDED_COMPONENT_PATH, "@crm/data-access", "PreferencesApi",
                        "loadDefinition", "Potwierdzenie operacji CRM.")
        ))).doesNotThrowAnyException();

        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                routeSlice("other-screen", "Proba wyjscia poza ekran CRM.")
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("selected screen reference");
        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                directSlice(API_PATH, "InventedApi", null, "Proba wyjscia poza graf CRM.")
        ))).doesNotThrowAnyException();
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                "{\"sliceRef\":\"crm-contact-preferences\",\"reason\":\"CRM\",\"projectName\":\"other\"}"
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("only sliceRef and reason");
    }

    @Test
    void shouldAllowFocusedReadIncludingSliceOwnerButRejectMissingReason() {
        var context = new UiExplorerCopilotToolSessionContextFactory().create(
                "crm-read-run",
                pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request(),
                context()
        );
        var validChunk = chunk(FETCHED_VALIDATOR_PATH, "main", "Weryfikacja walidatora CRM.");

        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK, validChunk
        ))).doesNotThrowAnyException();
        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                chunk(EMBEDDED_COMPONENT_PATH, "main", "Ponowny odczyt CRM.")
        ))).doesNotThrowAnyException();
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                chunk(FETCHED_VALIDATOR_PATH, "main", "")
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void shouldKeepAllowingScopedCrmDiscoveryUntilTheSectionGoalIsReached() {
        var context = new UiExplorerCopilotToolSessionContextFactory().create(
                "crm-goal-driven-run",
                pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request(),
                context()
        );
        var search = """
                {
                  "projectNames": [],
                  "pathPrefixes": ["apps/crm-agent"],
                  "branchRef": "main",
                  "keywords": ["CrmPreferenceValidator"],
                  "reason": "Domkniecie kolejnej luki funkcjonalnej CRM."
                }
                """;
        var read = chunk(FETCHED_VALIDATOR_PATH, "main", "Domkniecie kolejnej reguly CRM.");

        for (var index = 0; index < 50; index++) {
            assertThatCode(() -> scopePolicy.beforeInvocation(request(
                    context, GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES, search
            ))).doesNotThrowAnyException();
            assertThatCode(() -> scopePolicy.beforeInvocation(request(
                    context, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK, read
            ))).doesNotThrowAnyException();
        }
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

    private String routeSlice(String sliceRef, String reason) {
        return """
                {
                  "sliceRef": "%s",
                  "reason": "%s"
                }
                """.formatted(sliceRef, reason);
    }

    private String directSlice(String filePath, String declaringTypeName, String memberName, String reason) {
        var members = memberName != null ? "[\"" + memberName + "\"]" : "[]";
        return """
                {
                  "filePath": "%s",
                  "declaringTypeName": "%s",
                  "memberNames": %s,
                  "reason": "%s"
                }
                """.formatted(filePath, declaringTypeName, members, reason);
    }

    private String importSlice(
            String consumerFilePath,
            String moduleSpecifier,
            String importedSymbol,
            String memberName,
            String reason
    ) {
        return """
                {
                  "consumerFilePath": "%s",
                  "moduleSpecifier": "%s",
                  "importedSymbol": "%s",
                  "memberNames": ["%s"],
                  "reason": "%s"
                }
                """.formatted(consumerFilePath, moduleSpecifier, importedSymbol, memberName, reason);
    }
}
