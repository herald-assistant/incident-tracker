package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlowExplorerCopilotToolAccessPolicyFactory {

    public FlowExplorerCopilotToolAccessPolicy create(List<ToolDefinition> registeredTools) {
        return FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(registeredTools);
    }
}
