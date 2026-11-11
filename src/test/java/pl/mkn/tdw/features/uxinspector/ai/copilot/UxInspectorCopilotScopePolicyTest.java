package pl.mkn.tdw.features.uxinspector.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetProperties;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetRegistry;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.BudgetMode;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.LinkedHashMap;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.targetContext;

class UxInspectorCopilotScopePolicyTest {

    @Test
    void shouldKeepFullCatalogNavigationIndependentOfTheBoundRepositoryAfterResume() {
        var factory = new UxInspectorCopilotToolSessionContextFactory();
        var properties = new CopilotToolBudgetProperties();
        properties.setMode(BudgetMode.HARD);
        var registry = new CopilotToolBudgetRegistry(properties);
        var budget = new CopilotToolBudgetPolicy(registry);
        var contexts = java.util.List.of(factory.create("crm-catalog-run", targetContext()),
                factory.createFollowUp("crm-catalog-run", "crm-resumed-session", targetContext()));
        for (var session : contexts) {
            registry.registerSession(session.copilotSessionId());
            for (var index = 0; index < 50; index++) {
                var read = request(session, OperationalContextToolNames.GET_ENTITY,
                        "{\"type\":\"process\",\"id\":\"crm-contact-policy\",\"include\":[\"navigationDetails\"]}");
                assertThatCode(() -> policy.beforeInvocation(read)).doesNotThrowAnyException();
                assertThatCode(() -> budget.beforeInvocation(read)).doesNotThrowAnyException();
            }

        }
    }
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
    void shouldRejectApplicationExpansion() {
        var context = new UxInspectorCopilotToolSessionContextFactory().create("crm-scope-run", targetContext());
        assertThatThrownBy(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE, """
                {"projectName":"crm-ui","branchRef":"main","applicationNames":["crm-agent-portal"],
                 "filePath":"README.md","reason":"Proba rozszerzenia scope."}
                """))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("applicationNames");
    }

    @Test
    void shouldRejectUnsafeOpenApiLocator() {
        var context = new UxInspectorCopilotToolSessionContextFactory().create("crm-scope-run", targetContext());

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
    void shouldKeepStrictOpenApiLimits() {
        var context = new UxInspectorCopilotToolSessionContextFactory().create("crm-run", targetContext());
        for (var extra : java.util.List.of("\"schemaDepth\":5", "\"maxCharacters\":999", "\"maxCharacters\":50001")) {
            assertThatThrownBy(() -> policy.beforeInvocation(request(context, GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE,
                    "{\"filePath\":\"contracts/customer.json\",\"operationId\":\"getCustomer\"," + extra + "}")))
                    .isInstanceOf(CopilotToolInvocationRejectedException.class);
        }
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
