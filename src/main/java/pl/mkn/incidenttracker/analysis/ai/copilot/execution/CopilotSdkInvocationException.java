package pl.mkn.incidenttracker.analysis.ai.copilot.execution;

public class CopilotSdkInvocationException extends RuntimeException {

    public CopilotSdkInvocationException(String message) {
        super(message);
    }

    public CopilotSdkInvocationException(String message, Throwable cause) {
        super(message, unwrap(cause));
    }

    static Throwable unwrap(Throwable throwable) {
        var current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

}
