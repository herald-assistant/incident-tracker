package pl.mkn.tdw.shared.ai.chat;

import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

/** Captures one assistant turn for synchronous local-history continuation. */
public final class AnalysisChatAssistantCapture {

    private final List<AnalysisEvidenceSection> evidence = new ArrayList<>();
    private final List<AnalysisAiActivityEvent> activity = new ArrayList<>();
    private final List<AnalysisAiToolFeedback> feedback = new ArrayList<>();

    public void addToolEvidence(AnalysisEvidenceSection section) {
        if (section == null || !section.hasItems()) return;
        if (AnalysisAiToolFeedbackEvidenceMapper.isToolFeedbackSection(section)) {
            AnalysisAiToolFeedbackEvidenceMapper.fromSection(section).forEach(candidate -> {
                if (feedback.stream().noneMatch(current -> current.feedbackId().equals(candidate.feedbackId()))) {
                    feedback.add(candidate);
                }
            });
            return;
        }
        evidence.removeIf(current -> current.provider().equals(section.provider())
                && current.category().equals(section.category()));
        evidence.add(section);
    }

    public void addActivity(AnalysisAiActivityEvent event) {
        if (event != null) activity.add(event);
    }

    public List<AnalysisEvidenceSection> toolEvidenceSections() { return List.copyOf(evidence); }
    public List<AnalysisAiActivityEvent> aiActivityEvents() { return List.copyOf(activity); }
    public List<AnalysisAiToolFeedback> toolFeedback() { return List.copyOf(feedback); }
}
