package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public class CopilotSdkInvocationException extends RuntimeException {

    private final AnalysisAiUsage usage;

    public CopilotSdkInvocationException(String message) { this(message, null, null); }

    public CopilotSdkInvocationException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public CopilotSdkInvocationException(String message, Throwable cause, AnalysisAiUsage usage) {
        super(message, cause != null ? unwrap(cause) : null);
        this.usage = usage;
    }

    public AnalysisAiUsage usage() { return usage; }

    static Throwable unwrap(Throwable throwable) {
        var current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

}
