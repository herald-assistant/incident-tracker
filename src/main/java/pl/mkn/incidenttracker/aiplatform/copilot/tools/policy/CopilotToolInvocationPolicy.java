package pl.mkn.incidenttracker.aiplatform.copilot.tools.policy;

public interface CopilotToolInvocationPolicy {

    default void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
    }

    default void afterInvocation(CopilotToolInvocationPolicyResult result) {
    }
}
