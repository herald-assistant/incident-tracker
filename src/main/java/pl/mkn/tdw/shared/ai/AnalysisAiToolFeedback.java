package pl.mkn.tdw.shared.ai;

import java.time.Instant;

public record AnalysisAiToolFeedback(
        String feedbackId,
        String targetToolName,
        String targetToolCallId,
        String feedbackToolCallId,
        String usefulness,
        String expectedDataReceived,
        String issueCategory,
        String improvementArea,
        String confidence,
        String summaryForOperator,
        String suggestedImprovement,
        Instant createdAt
) {
    public AnalysisAiToolFeedback {
        feedbackId = textOrEmpty(feedbackId);
        targetToolName = textOrEmpty(targetToolName);
        targetToolCallId = textOrEmpty(targetToolCallId);
        feedbackToolCallId = textOrEmpty(feedbackToolCallId);
        usefulness = textOrEmpty(usefulness);
        expectedDataReceived = textOrEmpty(expectedDataReceived);
        issueCategory = textOrEmpty(issueCategory);
        improvementArea = textOrEmpty(improvementArea);
        confidence = textOrEmpty(confidence);
        summaryForOperator = textOrEmpty(summaryForOperator);
        suggestedImprovement = textOrEmpty(suggestedImprovement);
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    private static String textOrEmpty(String value) {
        return value != null ? value : "";
    }
}
