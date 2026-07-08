package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class FlowExplorerCopilotRedundantDiscoveryPolicy implements CopilotToolInvocationPolicy {

    private static final List<String> GROUNDING_ARTIFACTS = List.of(
            "flow-explorer/canonical-tool-inputs.md",
            "flow-explorer/compact-flow-manifest.md",
            "flow-explorer/snippet-cards.md",
            "flow-explorer/context-snapshot.json"
    );

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (!flowExplorerInitialRun(request)) {
            return;
        }

        if (GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT.equals(request.toolName())
                && endpointContextEmbedded(request)) {
            reject(
                    request,
                    "Endpoint use-case context is already embedded in the initial Flow Explorer artifacts.",
                    "Nie uruchamiaj ponownie broad endpoint discovery. Uzyj `flow-explorer/canonical-tool-inputs.md`, `flow-explorer/compact-flow-manifest.md` i `flow-explorer/snippet-cards.md`; jezeli nadal brakuje danych, uzyj focused read dla konkretnego pliku/metody. `pathPrefixes` sa domyslnym discovery scope, nie blokada dla jawnie wskazanej sciezki."
            );
        }

        if (GitLabToolNames.LIST_AVAILABLE_REPOSITORIES.equals(request.toolName())
                && repositoryScopeResolved(request)) {
            reject(
                    request,
                    "Repository scope is already resolved for this Flow Explorer initial run.",
                    "Nie wykonuj repository rediscovery. Uzyj `projectName`, `projectPath`, `branchRef`, `searchMode` i `pathPrefixes` z `flow-explorer/canonical-tool-inputs.md` oraz `filePath` z `flow-explorer/compact-flow-manifest.md`; jezeli potrzebujesz kodu, wykonaj focused GitLab read na konkretnym pliku albo metodzie, rowniez poza domyslnym discovery scope z jawna notka widocznosci."
            );
        }
    }

    private boolean flowExplorerInitialRun(CopilotToolInvocationPolicyRequest request) {
        var context = hiddenContext(request);
        return FlowExplorerCopilotToolContextKeys.FEATURE_VALUE.equals(
                context.get(FlowExplorerCopilotToolContextKeys.FEATURE)
        ) && FlowExplorerCopilotToolContextKeys.RUN_KIND_INITIAL.equals(
                context.get(FlowExplorerCopilotToolContextKeys.RUN_KIND)
        );
    }

    private boolean endpointContextEmbedded(CopilotToolInvocationPolicyRequest request) {
        return Boolean.TRUE.equals(hiddenContext(request).get(
                FlowExplorerCopilotToolContextKeys.ENDPOINT_CONTEXT_EMBEDDED
        ));
    }

    private boolean repositoryScopeResolved(CopilotToolInvocationPolicyRequest request) {
        return Boolean.TRUE.equals(hiddenContext(request).get(
                FlowExplorerCopilotToolContextKeys.REPOSITORY_SCOPE_RESOLVED
        ));
    }

    private Map<String, Object> hiddenContext(CopilotToolInvocationPolicyRequest request) {
        return request != null && request.sessionContext() != null
                ? request.sessionContext().hiddenContext()
                : Map.of();
    }

    private void reject(CopilotToolInvocationPolicyRequest request, String reason, String instruction) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "denied_by_flow_explorer_policy");
        result.put("toolName", request.toolName());
        result.put("toolCallId", request.toolCallId());
        result.put("reason", reason);
        result.put("instruction", instruction);
        result.put("groundingArtifacts", GROUNDING_ARTIFACTS);
        result.put("retryableWithChangedArguments", false);
        throw new CopilotToolInvocationRejectedException(reason, result);
    }
}
