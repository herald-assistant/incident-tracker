package pl.mkn.tdw.features.releasesummary.job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.shared.ai.report.AnalysisReportEditRequest;
@Component
@RequiredArgsConstructor
public class ReleaseSummaryLocalRunHandler implements LocalAnalysisRunChatHandler {
    private final ReleaseSummaryJobService jobs;
    private final ReleaseSummaryPersistence persistence;
    @Override public String feature(){return ReleaseSummaryPersistence.FEATURE;}
    @Override public boolean canContinue(LocalAnalysisRunIndexEntry index,LocalAnalysisRunRecord record) {
        try{return jobs.get(index.analysisId()).chatAvailability().available();}catch(RuntimeException e){return false;}
    }
    @Override public LocalAnalysisRunChatResult continueRun(LocalAnalysisRunIndexEntry index,LocalAnalysisRunRecord record,String message) {
        jobs.continueUnderLease(index.analysisId(),message);return saved(index.analysisId());
    }
    @Override public LocalAnalysisRunChatResult editReport(LocalAnalysisRunIndexEntry index,LocalAnalysisRunRecord record,AnalysisReportEditRequest request) {
        jobs.editUnderLease(index.analysisId(),request);return saved(index.analysisId());
    }
    private LocalAnalysisRunChatResult saved(String id) {
        return new LocalAnalysisRunChatResult(persistence.find(id).orElseThrow(),jobs.get(id).updatedAt());
    }
}
