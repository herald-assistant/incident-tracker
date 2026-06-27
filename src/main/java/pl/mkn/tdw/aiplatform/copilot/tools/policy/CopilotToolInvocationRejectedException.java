package pl.mkn.tdw.aiplatform.copilot.tools.policy;

public class CopilotToolInvocationRejectedException extends RuntimeException {

    private final Object result;

    public CopilotToolInvocationRejectedException(String message, Object result) {
        super(message);
        this.result = result;
    }

    public Object result() {
        return result;
    }
}
