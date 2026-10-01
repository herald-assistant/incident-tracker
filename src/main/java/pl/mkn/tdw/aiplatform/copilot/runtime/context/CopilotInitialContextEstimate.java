package pl.mkn.tdw.aiplatform.copilot.runtime.context;

public record CopilotInitialContextEstimate(long promptTokens, long reservedTokens) {

    public long totalTokens() {
        return promptTokens > Long.MAX_VALUE - reservedTokens
                ? Long.MAX_VALUE : promptTokens + reservedTokens;
    }
}
