package pl.mkn.tdw.features.releasesummary.contract;

import pl.mkn.tdw.shared.ai.*;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService.Result;
import java.time.Instant;
import java.util.List;

public record ReleaseSummarySnapshot(String jobId,String status,ReleaseSummaryRequest request,
        ReleaseSummaryScope comparison,ReleaseSummaryEvidence evidence,String errorCode,String error,
        Instant createdAt,Instant updatedAt,Instant completedAt,List<AnalysisJobStepResponse> steps,
        List<AnalysisEvidenceSection> contextSections,List<AnalysisEvidenceSection> toolEvidenceSections,
        List<AnalysisAiActivityEvent> aiActivityEvents,List<AnalysisAiToolFeedback> toolFeedback,
        String preparedPrompt,AnalysisReport report,Result result,AnalysisAiUsage usage,
        List<AnalysisChatMessageResponse> chatMessages,ChatAvailability chatAvailability,boolean exportAvailable) {
    public record ChatAvailability(boolean available,String code,String message) {}
    public ReleaseSummarySnapshot {
        steps=steps==null?List.of():List.copyOf(steps);
        contextSections=contextSections==null?List.of():List.copyOf(contextSections);
        toolEvidenceSections=toolEvidenceSections==null?List.of():List.copyOf(toolEvidenceSections);
        aiActivityEvents=aiActivityEvents==null?List.of():List.copyOf(aiActivityEvents);
        toolFeedback=toolFeedback==null?List.of():List.copyOf(toolFeedback);
        chatMessages=chatMessages==null?List.of():List.copyOf(chatMessages);
    }
}

