package pl.mkn.tdw.shared.ai.chat;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Neutral mutable projection used by feature-owned job states. */
public final class AnalysisChatMessageState {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    private final String id;
    private final String role;
    private final Instant createdAt;
    private final List<AnalysisEvidenceSection> toolEvidenceSections = new ArrayList<>();
    private final List<AnalysisAiActivityEvent> aiActivityEvents = new ArrayList<>();
    private final List<AnalysisAiToolFeedback> toolFeedback = new ArrayList<>();
    private String status;
    private String content;
    private String errorCode;
    private String errorMessage;
    private String prompt;
    private AnalysisAiUsage usage;
    private Instant updatedAt;
    private Instant completedAt;

    private AnalysisChatMessageState(String id, String role, String status, String content, Instant createdAt) {
        this.id = id;
        this.role = role;
        this.status = status;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.completedAt = COMPLETED.equals(status) ? createdAt : null;
    }

    public static AnalysisChatMessageState completedUser(String id, String content, Instant createdAt) {
        return new AnalysisChatMessageState(id, USER, COMPLETED, content, createdAt);
    }

    public static AnalysisChatMessageState inProgressAssistant(String id, Instant createdAt) {
        return new AnalysisChatMessageState(id, ASSISTANT, IN_PROGRESS, "", createdAt);
    }

    public void addToolEvidence(AnalysisEvidenceSection section) {
        if (section == null || !section.hasItems()) return;
        if (AnalysisAiToolFeedbackEvidenceMapper.isToolFeedbackSection(section)) {
            for (var feedback : AnalysisAiToolFeedbackEvidenceMapper.fromSection(section)) {
                if (toolFeedback.stream().noneMatch(current -> current.feedbackId().equals(feedback.feedbackId()))) {
                    toolFeedback.add(feedback);
                }
            }
        } else {
            for (var index = 0; index < toolEvidenceSections.size(); index++) {
                var current = toolEvidenceSections.get(index);
                if (current.provider().equals(section.provider()) && current.category().equals(section.category())) {
                    toolEvidenceSections.set(index, section);
                    updatedAt = Instant.now();
                    return;
                }
            }
            toolEvidenceSections.add(section);
        }
        updatedAt = Instant.now();
    }

    public void addActivity(AnalysisAiActivityEvent event) {
        if (event != null && (event.eventId() == null || aiActivityEvents.stream()
                .noneMatch(current -> event.eventId().equals(current.eventId())))) {
            aiActivityEvents.add(event);
            updatedAt = Instant.now();
        }
    }

    public void complete(String content, String prompt, AnalysisAiUsage usage) {
        status = COMPLETED;
        this.content = StringUtils.hasText(content) ? content.trim() : "";
        this.prompt = StringUtils.hasText(prompt) ? prompt.trim() : null;
        this.usage = usage;
        completedAt = Instant.now();
        updatedAt = completedAt;
    }

    public void fail(String code, String message) {
        status = FAILED;
        errorCode = code;
        errorMessage = message;
        completedAt = Instant.now();
        updatedAt = completedAt;
    }

    public boolean activeAssistant() {
        return ASSISTANT.equals(role) && IN_PROGRESS.equals(status);
    }

    public String id() { return id; }
    public String role() { return role; }
    public String status() { return status; }
    public String content() { return content; }

    public AnalysisChatMessageResponse snapshot() {
        return new AnalysisChatMessageResponse(id, role, status, content, errorCode, errorMessage,
                createdAt, updatedAt, completedAt, toolEvidenceSections, aiActivityEvents, toolFeedback, prompt, usage);
    }
}
