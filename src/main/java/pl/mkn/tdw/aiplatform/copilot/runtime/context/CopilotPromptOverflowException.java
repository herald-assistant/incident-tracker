package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkInvocationException;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import java.util.regex.Pattern;

public class CopilotPromptOverflowException extends CopilotSdkInvocationException {
    private static final Pattern PROVIDER_OVERFLOW = Pattern.compile(
            "prompt token count of\\s+(\\d+)\\s+exceeds the limit of\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private final long inputTokens;
    private final long promptTokenLimit;

    public CopilotPromptOverflowException(long inputTokens, long promptTokenLimit, Throwable cause, AnalysisAiUsage usage) {
        super("Wejście AI wymaga około " + inputTokens + " tokenów; dostępny limit promptu: " + promptTokenLimit + ".", cause, usage);
        this.inputTokens = inputTokens;
        this.promptTokenLimit = promptTokenLimit;
    }
    public long inputTokens() { return inputTokens; }
    public long promptTokenLimit() { return promptTokenLimit; }

    public static CopilotPromptOverflowException fromProvider(Throwable failure, AnalysisAiUsage usage) {
        for (var current = failure; current != null; current = current.getCause()) {
            var matcher = PROVIDER_OVERFLOW.matcher(String.valueOf(current.getMessage()));
            if (matcher.find()) {
                try {
                    var input = Long.parseLong(matcher.group(1));
                    var limit = Long.parseLong(matcher.group(2));
                    if (limit > 0 && input > limit) return new CopilotPromptOverflowException(input, limit, failure, usage);
                } catch (NumberFormatException ignored) { /* Do not reclassify an unrecognized provider failure. */ }
            }
            if (current.getCause() == current) break;
        }
        return null;
    }
}
