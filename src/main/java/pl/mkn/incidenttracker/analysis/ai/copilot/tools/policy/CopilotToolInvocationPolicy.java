package pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy;

public interface CopilotToolInvocationPolicy {

    default void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
    }

    default void afterInvocation(CopilotToolInvocationPolicyResult result) {
    }
}
