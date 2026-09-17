package pl.mkn.tdw.features.uxinspector.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.LinkedHashMap;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.targetContext;

class UxInspectorCopilotScopePolicyTest {
    private final UxInspectorCopilotScopePolicy policy = new UxInspectorCopilotScopePolicy(new ObjectMapper());

    @Test
    void shouldAllowNavigationAndReadsAcrossTheSelectedRepository() {
        var context = new UxInspectorCopilotToolSessionContextFactory().create("crm-scope-run", targetContext());

        assertThatCode(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE, """
                {
                  "projectName": "crm-ui",
                  "branchRef": "main",
                  "filePath": "pom.xml",
                  "reason": "Sprawdzenie konfiguracji wybranego repozytorium."
                }
                """))).doesNotThrowAnyException();

        assertThatCode(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK, """
                {
                  "projectName": "crm-ui",
                  "branchRef": "main",
                  "filePath": "src/main/java/example/crm/ContactPolicy.java",
                  "startLine": 1,
                  "endLine": 900,
                  "reason": "Potwierdzenie reguly biznesowej."
                }
                """))).doesNotThrowAnyException();

        assertThatCode(() -> policy.beforeInvocation(request(context, GitLabToolNames.LIST_REPOSITORY_TREE, """
                {"projectName":"crm-ui","branchRef":"main","path":"","reason":"Przeglad struktury."}
                """))).doesNotThrowAnyException();
        assertThatCode(() -> policy.beforeInvocation(request(context, GitLabToolNames.LIST_REPOSITORY_FILES, """
                {"projectName":"crm-ui","branchRef":"main","pathPrefix":".github","reason":"Lista instrukcji."}
                """))).doesNotThrowAnyException();
        assertThatCode(() -> policy.beforeInvocation(request(context, GitLabToolNames.SEARCH_REPOSITORY_FILES, """
                {"projectName":"crm-ui","branchRef":"main","query":"ContactPolicy","pathPrefix":"",
                 "reason":"Wyszukanie reguly."}
                """))).doesNotThrowAnyException();
        assertThatCode(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE, """
                {"projectName":"crm-ui","branchRef":"main","filePath":"contracts/customer-api.json",
                 "operationId":"getCustomer","schemaDepth":2,"maxCharacters":20000,
                 "reason":"Potwierdzenie kontraktu wywolania klienta."}
                """))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectProjectBranchPathAndApplicationExpansion() {
        var context = new UxInspectorCopilotToolSessionContextFactory().create("crm-scope-run", targetContext());
        var valid = """
                {
                  "projectName": "crm-ui",
                  "branchRef": "main",
                  "filePath": "pom.xml",
                  "reason": "Potwierdzenie zrodla danych."
                }
                """;

        assertThatThrownBy(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE,
                valid.replace("crm-ui", "other-project"))))
                .isInstanceOf(CopilotToolInvocationRejectedException.class).hasMessageContaining("projectName");
        assertThatThrownBy(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE,
                valid.replace("\"main\"", "\"other\""))))
                .isInstanceOf(CopilotToolInvocationRejectedException.class).hasMessageContaining("branchRef");
        assertThatThrownBy(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE,
                valid.replace("pom.xml", "src/app/../secrets.txt"))))
                .isInstanceOf(CopilotToolInvocationRejectedException.class).hasMessageContaining("filePath");
        assertThatThrownBy(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE, """
                {"projectName":"crm-ui","branchRef":"main","applicationNames":["crm-agent-portal"],
                 "filePath":"README.md","reason":"Proba rozszerzenia scope."}
                """))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("applicationNames");
    }

    @Test
    void shouldRejectUnexplainedReadsAndIgnoreOtherTools() {
        var context = new UxInspectorCopilotToolSessionContextFactory().create("crm-scope-run", targetContext());

        assertThatThrownBy(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE, """
                {
                  "projectName": "crm-ui",
                  "branchRef": "main",
                  "filePath": "README.md"
                }
                """)))
                .isInstanceOf(CopilotToolInvocationRejectedException.class).hasMessageContaining("reason");
        assertThatCode(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                "not-json"))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnsafeOpenApiLocator() {
        var context = new UxInspectorCopilotToolSessionContextFactory().create("crm-scope-run", targetContext());

        assertThatThrownBy(() -> policy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE,
                """
                        {"projectName":"crm-ui","branchRef":"main","filePath":"contracts/customer-api.json",
                         "reason":"Proba odczytu bez lokatora."}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class).hasMessageContaining("operationId");

        assertThatThrownBy(() -> policy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE,
                """
                        {"projectName":"crm-ui","branchRef":"main","filePath":"contracts/customer-api.txt",
                         "operationId":"getCustomer","reason":"Proba odczytu zlego formatu."}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class).hasMessageContaining(".json");
    }

    @Test
    void shouldFailClosedWhenRepositoryScopeIsUnavailable() {
        var context = new UxInspectorCopilotToolSessionContextFactory().create("crm-scope-run", targetContext());
        var hidden = new LinkedHashMap<>(context.hiddenContext());
        hidden.remove(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE);
        var unscoped = new CopilotToolSessionContext(context.analysisRunId(), context.copilotSessionId(), hidden);

        assertThatThrownBy(() -> policy.beforeInvocation(request(unscoped, GitLabToolNames.READ_REPOSITORY_FILE, """
                {
                  "projectName": "crm-ui",
                  "branchRef": "main",
                  "filePath": "README.md",
                  "reason": "Potwierdzenie zrodla danych."
                }
                """)))
                .isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("scope is unavailable");
    }

    private CopilotToolInvocationPolicyRequest request(
            pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext context,
            String toolName,
            String arguments
    ) {
        return new CopilotToolInvocationPolicyRequest(
                context, context.copilotSessionId(), "crm-tool-call", toolName, arguments
        );
    }
}
