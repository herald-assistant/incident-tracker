package pl.mkn.tdw.shared.ai;

import java.time.Instant;
import java.util.List;

/** Operator-visible record of one AI request, including unsuccessful attempts. */
public record AnalysisAiInvocation(
        String invocationId, String role, String status, int partNumber, int partCount,
        List<String> evidenceScope, long estimatedInputTokens, long promptTokenLimit,
        int reservedTokens, String preparedPrompt, String rawResponse,
        List<AnalysisAiFinding> findings, List<String> visibilityLimits, String sessionId,
        AnalysisAiUsage usage, String errorMessage, Instant startedAt, Instant completedAt
) {
    public AnalysisAiInvocation {
        evidenceScope = evidenceScope != null ? List.copyOf(evidenceScope) : List.of();
        findings = findings != null ? List.copyOf(findings) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public AnalysisAiInvocation response(String raw, String session, AnalysisAiUsage consumed) {
        return new AnalysisAiInvocation(invocationId, role, "RAW_RESPONSE", partNumber, partCount, evidenceScope,
                estimatedInputTokens, promptTokenLimit, reservedTokens, preparedPrompt, raw,
                findings, visibilityLimits, session, consumed, null, startedAt, null);
    }
    public AnalysisAiInvocation completed(List<AnalysisAiFinding> facts, List<String> limits) {
        return new AnalysisAiInvocation(invocationId, role, "COMPLETED", partNumber, partCount, evidenceScope,
                estimatedInputTokens, promptTokenLimit, reservedTokens, preparedPrompt, rawResponse,
                facts, limits, sessionId, usage, null, startedAt, Instant.now());
    }
    public AnalysisAiInvocation failed(String message, AnalysisAiUsage consumed) {
        return new AnalysisAiInvocation(invocationId, role, "FAILED", partNumber, partCount, evidenceScope,
                estimatedInputTokens, promptTokenLimit, reservedTokens, preparedPrompt, rawResponse,
                findings, visibilityLimits, sessionId, consumed != null ? consumed : usage, message, startedAt, Instant.now());
    }

    public AnalysisAiInvocation withPartCount(int count) {
        return new AnalysisAiInvocation(invocationId, role, status, partNumber, count, evidenceScope,
                estimatedInputTokens, promptTokenLimit, reservedTokens, preparedPrompt, rawResponse,
                findings, visibilityLimits, sessionId, usage, errorMessage, startedAt, completedAt);
    }
}
