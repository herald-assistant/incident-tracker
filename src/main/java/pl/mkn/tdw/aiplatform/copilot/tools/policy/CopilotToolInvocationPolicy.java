package pl.mkn.tdw.aiplatform.copilot.tools.policy;

public interface CopilotToolInvocationPolicy {

    default void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
    }

    default void afterInvocation(CopilotToolInvocationPolicyResult result) {
    }
}
