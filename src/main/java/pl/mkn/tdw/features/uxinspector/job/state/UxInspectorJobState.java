package pl.mkn.tdw.features.uxinspector.job.state;

import pl.mkn.tdw.features.uxinspector.ai.UxInspectorAiAnalysis;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorAiAnalysisStatus;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorSourceRevision;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.job.api.*;
import pl.mkn.tdw.shared.ai.*;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobChatUnavailableException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UxInspectorJobState {
    public static final String TARGET_STEP = "TARGET_RESOLUTION";
    public static final String PREPARATION_STEP = "AI_PREPARATION";
    public static final String ANALYSIS_STEP = "AI_ANALYSIS";
    private final String jobId;
    private final UxInspectorJobStartRequest request;
    private final Instant createdAt = Instant.now();
    private final Map<String, MutableStep> steps = new LinkedHashMap<>();
    private boolean claimed;
    private UxInspectorJobStatus status = UxInspectorJobStatus.QUEUED;
    private String currentStepCode = TARGET_STEP;
    private String currentStepLabel = "Resolve the selected browser target";
    private String errorCode;
    private String errorMessage;
    private Instant updatedAt = createdAt;
    private Instant completedAt;
    private String systemLabel;
    private pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus resolutionStatus;
    private int candidateCount;
    private UxInspectorSourceRevision sourceRevision;
    private List<AnalysisEvidenceSection> contextSections = List.of();
    private List<AnalysisEvidenceSection> toolEvidenceSections = List.of();
    private List<AnalysisAiActivityEvent> aiActivityEvents = List.of();
    private List<AnalysisAiToolFeedback> toolFeedback = List.of();
    private String preparedPrompt;
    private pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse result;
    private AnalysisReport report;
    private AnalysisAiUsage usage;
    private String copilotSessionId;
    private final List<AnalysisChatMessageState> chatMessages = new ArrayList<>();

    public UxInspectorJobState(String jobId, UxInspectorJobStartRequest request) {
        this.jobId = jobId;
        this.request = request;
        sourceRevision = new UxInspectorSourceRevision(request.branch(), request.sourceRevision());
        steps.put(TARGET_STEP, new MutableStep(TARGET_STEP, "Resolve the selected browser target", "CONTEXT"));
        steps.put(PREPARATION_STEP, new MutableStep(PREPARATION_STEP, "Prepare focused AI context", "AI_PREPARATION"));
        steps.put(ANALYSIS_STEP, new MutableStep(ANALYSIS_STEP, "Answer the focused UX question", "AI"));
    }

    public synchronized boolean tryStart() {
        if (claimed) return false;
        claimed = true;
        status = UxInspectorJobStatus.RESOLVING_TARGET;
        start(TARGET_STEP, "The element is being matched inside the selected view and pinned revision.");
        return true;
    }

    public synchronized void targetResolved(UxInspectorTargetContext context, List<AnalysisEvidenceSection> evidence) {
        systemLabel = context.systemLabel();
        resolutionStatus = context.status();
        candidateCount = context.candidates().size();
        sourceRevision = context.sourceRevision();
        contextSections = evidence != null ? List.copyOf(evidence) : List.of();
        complete(TARGET_STEP, context.status() == pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus.RESOLVED
                ? "COMPLETED" : "PARTIAL", "Target resolution finished with status " + context.status() + ".",
                candidateCount, null);
        status = UxInspectorJobStatus.PREPARING_AI;
        currentStepCode = PREPARATION_STEP;
        currentStepLabel = steps.get(PREPARATION_STEP).label;
        updatedAt = Instant.now();
    }

    public synchronized void preparationStarted() { start(PREPARATION_STEP, "Focused context, component source pack and report contract are being prepared."); }
    public synchronized void preparationCompleted(String prompt, int artifactCount) {
        preparedPrompt = prompt;
        complete(PREPARATION_STEP, "COMPLETED",
                "Pinned runtime, repository guidance and component source pack prepared.",
                artifactCount, null);
        currentStepCode = ANALYSIS_STEP;
        currentStepLabel = steps.get(ANALYSIS_STEP).label;
        updatedAt = Instant.now();
    }
    public synchronized void analysisStarted() {
        status = UxInspectorJobStatus.ANALYZING;
        start(ANALYSIS_STEP, "Copilot is answering the single UX Inspector question.");
    }

    public synchronized void toolEvidence(AnalysisEvidenceSection section) {
        if (section == null || !section.hasItems()) return;
        if (AnalysisAiToolFeedbackEvidenceMapper.isToolFeedbackSection(section)) {
            var updated = new ArrayList<>(toolFeedback);
            for (var value : AnalysisAiToolFeedbackEvidenceMapper.fromSection(section)) {
                if (updated.stream().noneMatch(existing -> existing.feedbackId().equals(value.feedbackId()))) updated.add(value);
            }
            toolFeedback = List.copyOf(updated);
        } else {
            var updated = new ArrayList<>(toolEvidenceSections);
            updated.removeIf(value -> value.provider().equals(section.provider()) && value.category().equals(section.category()));
            updated.add(section);
            toolEvidenceSections = List.copyOf(updated);
        }
        updatedAt = Instant.now();
    }

    public synchronized void activity(AnalysisAiActivityEvent event) {
        if (event == null || event.eventId() != null && aiActivityEvents.stream().anyMatch(value -> event.eventId().equals(value.eventId()))) return;
        var updated = new ArrayList<>(aiActivityEvents); updated.add(event); aiActivityEvents = List.copyOf(updated); updatedAt = Instant.now();
    }

    public synchronized void analysisCompleted(UxInspectorAiAnalysis analysis) {
        usage = analysis.usage();
        if (analysis.sessionId() != null && !analysis.sessionId().isBlank()) {
            copilotSessionId = analysis.sessionId().trim();
        }
        if (analysis.status() == UxInspectorAiAnalysisStatus.COMPLETED || analysis.status() == UxInspectorAiAnalysisStatus.PARTIAL) {
            if (analysis.result() == null || analysis.report() == null) {
                fail("UX_INSPECTOR_REPORT_UNAVAILABLE", "AI session finished without a valid report saved through report tools.");
                return;
            }
            status = analysis.status() == UxInspectorAiAnalysisStatus.COMPLETED ? UxInspectorJobStatus.COMPLETED : UxInspectorJobStatus.PARTIAL;
            result = analysis.result(); report = analysis.report();
            complete(ANALYSIS_STEP, status == UxInspectorJobStatus.COMPLETED ? "COMPLETED" : "PARTIAL",
                    status == UxInspectorJobStatus.COMPLETED ? "Focused answer completed." : "Answer completed with explicit limitations.",
                    1, usage);
            terminal();
            return;
        }
        if (analysis.status() == UxInspectorAiAnalysisStatus.BLOCKED) {
            block("UX_INSPECTOR_AI_BLOCKED", analysis.limitations().isEmpty() ? "UX Inspector analysis is blocked." : analysis.limitations().get(0));
        } else {
            fail("UX_INSPECTOR_REPORT_UNAVAILABLE", analysis.limitations().isEmpty()
                    ? "AI session did not save a valid report through report tools." : analysis.limitations().get(0));
        }
    }

    public synchronized void block(String code, String message) { terminate(UxInspectorJobStatus.BLOCKED, code, message, "BLOCKED"); }
    public synchronized void fail(String code, String message) { terminate(UxInspectorJobStatus.FAILED, code, message, "FAILED"); }

    public synchronized UxInspectorJobStateSnapshot snapshot() {
        var available = (status == UxInspectorJobStatus.COMPLETED || status == UxInspectorJobStatus.PARTIAL)
                && result != null && report != null;
        return new UxInspectorJobStateSnapshot(jobId,
                new UxInspectorJobRequestSnapshot(request.systemId(), systemLabel != null ? systemLabel : request.systemId(),
                        request.branch(), request.viewId(), request.sourceRevision(), request.question(), request.capture(),
                        request.model(), request.reasoningEffort(), resolutionStatus, candidateCount),
                status, currentStepCode, currentStepLabel, errorCode, errorMessage, createdAt, updatedAt, completedAt,
                steps.values().stream().map(MutableStep::snapshot).toList(), contextSections, toolEvidenceSections,
                aiActivityEvents, toolFeedback, preparedPrompt, result, report, usage, sourceRevision,
                new UxInspectorOutputAvailability(available ? "AVAILABLE" : "BLOCKED",
                        available ? "UX_INSPECTOR_OUTPUT_AVAILABLE" : errorCode != null ? errorCode : "UX_INSPECTOR_IN_PROGRESS",
                        available ? "Focused UX Inspector answer is available." : errorMessage != null ? errorMessage : "Analysis is still in progress.",
                        available ? List.of() : pendingSteps()), available && !hasActiveAssistant(),
                chatMessages.stream().map(AnalysisChatMessageState::snapshot).toList(), chatAvailability());
    }

    public synchronized String startChatMessage(String userMessageId, String assistantMessageId, String message) {
        if ((status != UxInspectorJobStatus.COMPLETED && status != UxInspectorJobStatus.PARTIAL)
                || report == null || result == null) {
            throw new UxInspectorJobChatUnavailableException("UX_INSPECTOR_CHAT_NOT_READY",
                    "Follow-up chat is available only after a completed UX Inspector report.");
        }
        if (copilotSessionId == null || copilotSessionId.isBlank()) {
            throw new UxInspectorJobChatUnavailableException("UX_INSPECTOR_CHAT_SESSION_UNAVAILABLE",
                    "The completed UX Inspector run does not have a resumable AI session.");
        }
        if (hasActiveAssistant()) {
            throw new UxInspectorJobChatUnavailableException("UX_INSPECTOR_CHAT_IN_PROGRESS",
                    "A follow-up response is already in progress for this UX Inspector run.");
        }
        var now = Instant.now();
        chatMessages.add(AnalysisChatMessageState.completedUser(userMessageId, message, now));
        chatMessages.add(AnalysisChatMessageState.inProgressAssistant(assistantMessageId, now));
        updatedAt = now;
        return copilotSessionId;
    }

    public synchronized void chatToolEvidence(String assistantId, AnalysisEvidenceSection section) {
        assistantMessage(assistantId).addToolEvidence(section); updatedAt = Instant.now();
    }
    public synchronized void chatActivity(String assistantId, AnalysisAiActivityEvent event) {
        assistantMessage(assistantId).addActivity(event); updatedAt = Instant.now();
    }
    public synchronized void chatCompleted(String assistantId, String content, String prompt,
                                           AnalysisAiUsage valueUsage, String latestSessionId) {
        chatCompleted(assistantId, content, prompt, valueUsage, latestSessionId, null);
    }
    public synchronized void chatCompleted(String assistantId, String content, String prompt,
            AnalysisAiUsage valueUsage, String latestSessionId,
            pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapping mapping) {
        if (mapping != null) { result = mapping.result(); report = mapping.report(); }
        if (latestSessionId != null && !latestSessionId.isBlank()) copilotSessionId = latestSessionId.trim();
        assistantMessage(assistantId).complete(content, prompt, valueUsage); updatedAt = Instant.now();
    }
    public synchronized void chatFailed(String assistantId, String code, String message) {
        assistantMessage(assistantId).fail(code, message); updatedAt = Instant.now();
    }
    public synchronized String copilotSessionId() { return copilotSessionId; }
    public synchronized UxInspectorJobStartRequest initialRequest() { return request; }
    public synchronized AnalysisReport currentReport() { return report; }

    private boolean hasActiveAssistant() { return chatMessages.stream().anyMatch(AnalysisChatMessageState::activeAssistant); }
    private AnalysisChatMessageState assistantMessage(String id) {
        return chatMessages.stream().filter(value -> value.id().equals(id) && value.activeAssistant()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown active assistant message: " + id));
    }
    private UxInspectorChatAvailability chatAvailability() {
        if (hasActiveAssistant()) return new UxInspectorChatAvailability(false, "UX_INSPECTOR_CHAT_IN_PROGRESS",
                "AI is preparing the current follow-up response.");
        if ((status == UxInspectorJobStatus.COMPLETED || status == UxInspectorJobStatus.PARTIAL)
                && report != null && result != null && copilotSessionId != null && !copilotSessionId.isBlank()) {
            return new UxInspectorChatAvailability(true, "UX_INSPECTOR_CHAT_AVAILABLE",
                    "Follow-up chat can continue the completed UX Inspector session.");
        }
        return new UxInspectorChatAvailability(false, "UX_INSPECTOR_CHAT_UNAVAILABLE",
                "Follow-up chat requires a completed report and a resumable AI session.");
    }

    private void terminate(UxInspectorJobStatus terminalStatus, String code, String message, String stepStatus) {
        status = terminalStatus; errorCode = code; errorMessage = message;
        var step = currentStepCode != null ? steps.get(currentStepCode) : null;
        if (step != null) complete(step.code, stepStatus, message, 0, usage);
        for (var value : steps.values()) if ("PENDING".equals(value.status)) complete(value.code, "SKIPPED", "Skipped after terminal failure.", 0, null);
        terminal();
    }
    private void terminal() { var now = Instant.now(); currentStepCode = null; currentStepLabel = null; completedAt = now; updatedAt = now; }
    private void start(String code, String message) { var value = steps.get(code); value.status = "RUNNING"; value.message = message; value.startedAt = Instant.now(); updatedAt = value.startedAt; }
    private void complete(String code, String status, String message, Integer count, AnalysisAiUsage valueUsage) {
        var value = steps.get(code); value.status = status; value.message = message; value.itemCount = count; value.usage = valueUsage;
        if (value.startedAt == null) value.startedAt = createdAt; value.completedAt = Instant.now(); updatedAt = value.completedAt;
    }
    private List<String> pendingSteps() { return steps.values().stream().filter(value -> !"COMPLETED".equals(value.status) && !"PARTIAL".equals(value.status)).map(value -> value.code).toList(); }
    private static AnalysisEvidenceReference evidence(String category) { return new AnalysisEvidenceReference("ux-inspector", category); }

    private static final class MutableStep {
        private final String code; private final String label; private final String phase;
        private String status = "PENDING"; private String message = "Waiting for the previous step.";
        private Integer itemCount; private Instant startedAt; private Instant completedAt; private AnalysisAiUsage usage;
        private MutableStep(String code, String label, String phase) { this.code = code; this.label = label; this.phase = phase; }
        private AnalysisJobStepResponse snapshot() {
            var consumes = PREPARATION_STEP.equals(code) ? List.of(evidence("target-resolution"))
                    : ANALYSIS_STEP.equals(code) ? List.of(evidence("ai-artifacts")) : List.<AnalysisEvidenceReference>of();
            var produces = TARGET_STEP.equals(code) ? List.of(evidence("target-resolution"))
                    : PREPARATION_STEP.equals(code) ? List.of(evidence("ai-artifacts")) : List.<AnalysisEvidenceReference>of();
            return new AnalysisJobStepResponse(code, label, phase, status, message, itemCount, startedAt, completedAt,
                    consumes, produces, usage);
        }
    }
}
