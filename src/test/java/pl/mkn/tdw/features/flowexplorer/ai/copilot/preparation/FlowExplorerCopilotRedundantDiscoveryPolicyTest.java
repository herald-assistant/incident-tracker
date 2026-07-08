package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerCopilotRedundantDiscoveryPolicyTest {

    private final FlowExplorerCopilotRedundantDiscoveryPolicy policy =
            new FlowExplorerCopilotRedundantDiscoveryPolicy();

    @Test
    void shouldRejectEndpointContextRebuildWhenInitialArtifactsAlreadyEmbedIt() {
        var exception = assertThrows(CopilotToolInvocationRejectedException.class, () -> policy.beforeInvocation(
                request(
                        GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT,
                        flowExplorerContext(true, true, false)
                )
        ));

        var result = result(exception);
        assertEquals("denied_by_flow_explorer_policy", result.get("status"));
        assertEquals(GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT, result.get("toolName"));
        assertTrue(result.get("instruction").toString().contains("compact-flow-manifest.md"));
        assertTrue(result.get("instruction").toString().contains("focused read"));
        assertTrue(result.get("instruction").toString().contains("pathPrefixes"));
        assertTrue(result.get("instruction").toString().contains("nie blokada"));
    }

    @Test
    void shouldRejectRepositoryRediscoveryWhenInitialRepositoryScopeIsResolved() {
        var exception = assertThrows(CopilotToolInvocationRejectedException.class, () -> policy.beforeInvocation(
                request(
                        GitLabToolNames.LIST_AVAILABLE_REPOSITORIES,
                        flowExplorerContext(true, true, false)
                )
        ));

        var result = result(exception);
        assertEquals("denied_by_flow_explorer_policy", result.get("status"));
        assertEquals(GitLabToolNames.LIST_AVAILABLE_REPOSITORIES, result.get("toolName"));
        assertTrue(result.get("instruction").toString().contains("canonical-tool-inputs.md"));
        assertTrue(result.get("instruction").toString().contains("compact-flow-manifest.md"));
        assertTrue(result.get("instruction").toString().contains("projectName"));
        assertTrue(result.get("instruction").toString().contains("searchMode"));
        assertTrue(result.get("instruction").toString().contains("poza domyslnym discovery scope"));
    }

    @Test
    void shouldAllowDiscoveryWhenInitialContextDoesNotHaveRequiredScope() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT,
                flowExplorerContext(false, true, false)
        )));
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.LIST_AVAILABLE_REPOSITORIES,
                flowExplorerContext(true, false, false)
        )));
    }

    @Test
    void shouldAllowFocusedGitLabReadsEvenWhenContextIsResolved() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.READ_JAVA_METHOD_SLICE,
                flowExplorerContext(true, true, false)
        )));
    }

    @Test
    void shouldLeaveFlowExplorerFollowUpDiscoveryAvailable() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT,
                flowExplorerContext(true, true, true)
        )));
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.LIST_AVAILABLE_REPOSITORIES,
                flowExplorerContext(true, true, true)
        )));
    }

    @Test
    void shouldIgnoreNonFlowExplorerSessions() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT,
                Map.of("correlationId", "crm-correlation-123")
        )));
    }

    private static CopilotToolInvocationPolicyRequest request(String toolName, Map<String, Object> hiddenContext) {
        var sessionContext = new CopilotToolSessionContext(
                "crm-flow-job-123",
                "flow-explorer-crm-flow-job-123",
                hiddenContext
        );
        return new CopilotToolInvocationPolicyRequest(
                sessionContext,
                sessionContext.copilotSessionId(),
                "tool-call-1",
                toolName,
                "{\"reason\":\"sprawdzam scope CRM endpointu\"}"
        );
    }

    private static Map<String, Object> flowExplorerContext(
            boolean endpointContextEmbedded,
            boolean repositoryScopeResolved,
            boolean followUp
    ) {
        var context = new LinkedHashMap<String, Object>();
        context.put(FlowExplorerCopilotToolContextKeys.FEATURE, FlowExplorerCopilotToolContextKeys.FEATURE_VALUE);
        context.put(
                FlowExplorerCopilotToolContextKeys.RUN_KIND,
                followUp
                        ? FlowExplorerCopilotToolContextKeys.RUN_KIND_FOLLOW_UP
                        : FlowExplorerCopilotToolContextKeys.RUN_KIND_INITIAL
        );
        context.put(FlowExplorerCopilotToolContextKeys.ENDPOINT_CONTEXT_EMBEDDED, endpointContextEmbedded);
        context.put(FlowExplorerCopilotToolContextKeys.REPOSITORY_SCOPE_RESOLVED, repositoryScopeResolved);
        return context;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(CopilotToolInvocationRejectedException exception) {
        return (Map<String, Object>) exception.result();
    }
}
