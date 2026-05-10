package pl.mkn.incidenttracker.shared.ai;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public final class AnalysisAiToolFeedbackEvidenceMapper {

    public static final String PROVIDER = "ai";
    public static final String CATEGORY = "tool-feedback";

    private AnalysisAiToolFeedbackEvidenceMapper() {
    }

    public static AnalysisEvidenceSection toSection(AnalysisAiToolFeedback feedback) {
        if (feedback == null) {
            return new AnalysisEvidenceSection(PROVIDER, CATEGORY, List.of());
        }

        return new AnalysisEvidenceSection(
                PROVIDER,
                CATEGORY,
                List.of(new AnalysisEvidenceItem(
                        "Tool feedback: " + textOrFallback(feedback.targetToolName(), "unknown tool"),
                        List.of(
                                attribute("feedbackId", feedback.feedbackId()),
                                attribute("targetToolName", feedback.targetToolName()),
                                attribute("targetToolCallId", feedback.targetToolCallId()),
                                attribute("feedbackToolCallId", feedback.feedbackToolCallId()),
                                attribute("usefulness", feedback.usefulness()),
                                attribute("expectedDataReceived", feedback.expectedDataReceived()),
                                attribute("issueCategory", feedback.issueCategory()),
                                attribute("improvementArea", feedback.improvementArea()),
                                attribute("confidence", feedback.confidence()),
                                attribute("summaryForOperator", feedback.summaryForOperator()),
                                attribute("suggestedImprovement", feedback.suggestedImprovement()),
                                attribute("createdAt", feedback.createdAt() != null ? feedback.createdAt().toString() : "")
                        )
                ))
        );
    }

    public static boolean isToolFeedbackSection(AnalysisEvidenceSection section) {
        return section != null
                && PROVIDER.equals(section.provider())
                && CATEGORY.equals(section.category());
    }

    public static List<AnalysisAiToolFeedback> fromSection(AnalysisEvidenceSection section) {
        if (!isToolFeedbackSection(section) || !section.hasItems()) {
            return List.of();
        }

        return section.items().stream()
                .map(AnalysisAiToolFeedbackEvidenceMapper::fromItem)
                .toList();
    }

    private static AnalysisAiToolFeedback fromItem(AnalysisEvidenceItem item) {
        return new AnalysisAiToolFeedback(
                attributeValue(item, "feedbackId"),
                attributeValue(item, "targetToolName"),
                attributeValue(item, "targetToolCallId"),
                attributeValue(item, "feedbackToolCallId"),
                attributeValue(item, "usefulness"),
                attributeValue(item, "expectedDataReceived"),
                attributeValue(item, "issueCategory"),
                attributeValue(item, "improvementArea"),
                attributeValue(item, "confidence"),
                attributeValue(item, "summaryForOperator"),
                attributeValue(item, "suggestedImprovement"),
                parseInstant(attributeValue(item, "createdAt"))
        );
    }

    private static AnalysisEvidenceAttribute attribute(String name, String value) {
        return new AnalysisEvidenceAttribute(name, value != null ? value : "");
    }

    private static String attributeValue(AnalysisEvidenceItem item, String name) {
        if (item == null || item.attributes() == null) {
            return "";
        }
        return item.attributes().stream()
                .filter(attribute -> name.equals(attribute.name()))
                .map(AnalysisEvidenceAttribute::value)
                .findFirst()
                .orElse("");
    }

    private static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.now();
        }
    }

    private static String textOrFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
